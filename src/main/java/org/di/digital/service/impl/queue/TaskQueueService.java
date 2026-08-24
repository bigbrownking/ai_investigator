package org.di.digital.service.impl.queue;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.di.digital.model.cases.Case;
import org.di.digital.model.queue.TaskQueue;
import org.di.digital.model.enums.file.TaskStatus;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.queue.TaskQueueRepository;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskQueueService {

    private final TaskQueueRepository taskQueueRepository;
    private final MongoTemplate mongoTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final CaseFileRepository caseFileRepository;
    private final CaseRepository caseRepository;

    @Value("${spring.rabbitmq.mediator.queue}")
    public String DOCUMENT_QUEUE;

    @Value("${scheduler.stuck-task.timeout-minutes:15}")
    private long stuckTimeoutMinutes;

    @Value("${scheduler.orphan-reconciliation.min-age-minutes:2}")
    private long orphanMinAgeMinutes;
    @PostConstruct
    public void onStartupCleanup() {
        int n = resetStuckProcessingTasks();
        log.info("Startup cleanup: reset {} stuck tasks", n);
        try {
            rabbitAdmin.purgeQueue(DOCUMENT_QUEUE, false);
            log.info("Purged RabbitMQ queue on startup");
        } catch (Exception e) {
            log.error("Failed to purge queue", e);
        }
    }

    public int resetStuckProcessingTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckTimeoutMinutes);
        Query query = new Query(Criteria.where("status").is(TaskStatus.PROCESSING)
                .and("sentToQueueAt").lt(cutoff));
        List<TaskQueue> stuck = mongoTemplate.find(query, TaskQueue.class);
        if (stuck.isEmpty()) return 0;
        stuck.forEach(task -> {
            task.setStatus(TaskStatus.PENDING);
            task.setSentToQueueAt(null);
            task.setLastHeartbeatAt(null);
        });
        taskQueueRepository.saveAll(stuck);
        log.warn("Reset {} stuck PROCESSING tasks (older than {} min) back to PENDING: {}",
                stuck.size(), stuckTimeoutMinutes,
                stuck.stream().map(TaskQueue::getCaseFileId).toList());
        return stuck.size();
    }
    public void retryTask(Long caseFileId, String userEmail, Long caseId,
                          String caseNumber, String fileName, String fileUrl, String language) {
        List<TaskQueue> failedTasks = taskQueueRepository
                .findByCaseFileIdAndStatus(caseFileId, TaskStatus.FAILED);

        if (!failedTasks.isEmpty()) {
            TaskQueue task = failedTasks.get(0);
            task.setStatus(TaskStatus.PENDING);
            task.setErrorMessage(null);
            task.setCompletedAt(null);
            task.setSentToQueueAt(null);
            taskQueueRepository.save(task);
            log.info("Task {} re-queued for caseFile {}", task.getId(), caseFileId);
        } else {
            log.warn("No FAILED task found for caseFileId {}, creating new task", caseFileId);
            addTaskToQueue(userEmail, caseId, caseNumber, fileName, fileUrl, caseFileId, language);
        }
    }
    public void addTaskToQueue(String userEmail, Long caseId, String caseNumber,
                               String fileName, String fileUrl, Long caseFileId, String language) {

        boolean exists = taskQueueRepository
                .existsByCaseFileIdAndStatusIn(
                        caseFileId,
                        List.of(TaskStatus.PENDING, TaskStatus.PROCESSING)
                );

        if (exists) {
            log.warn("Task for caseFileId {} already exists, skipping", caseFileId);
            return;
        }
        int priority = getCasePriority(caseId);

        TaskQueue task = TaskQueue.builder()
                .userEmail(userEmail)
                .caseFileId(caseFileId)
                .language(language)
                .caseId(caseId)
                .caseNumber(caseNumber)
                .fileName(fileName)
                .fileUrl(fileUrl)
                .status(TaskStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .priority(priority)
                .build();

        taskQueueRepository.save(task);
        log.info("Added task {} to queue for user {}", fileName, userEmail);
    }

    public TaskQueue getNextTaskByRoundRobin(List<Long> excludedCaseIds,
                                             List<String> excludedUsers) {
        int maxPriority = getMaxPendingPriority(excludedCaseIds, excludedUsers);

        // 1) считаем, сколько файлов каждого дела УЖE ушло из pending (обработано/в обработке)
        //    это "прогресс дела" — чем больше, тем позже его очередь
        Criteria progressMatch = Criteria.where("priority").is(maxPriority)
                .and("status").in(TaskStatus.PROCESSING, TaskStatus.COMPLETED);
        Aggregation progressAgg = Aggregation.newAggregation(
                Aggregation.match(progressMatch),
                Aggregation.group("caseId").count().as("sentCount")
        );
        Map<Long, Integer> sentByCase = new HashMap<>();
        mongoTemplate.aggregate(progressAgg, "task_queue", Document.class)
                .getMappedResults().forEach(d -> {
                    Object id = d.get("_id");
                    if (id != null) sentByCase.put(((Number) id).longValue(),
                            ((Number) d.get("sentCount")).intValue());
                });

        // 2) кандидаты: по одному самому раннему pending-файлу на каждое (user, case),
        //    плюс userFirstTask для тай-брейка
        Criteria match = Criteria.where("status").is(TaskStatus.PENDING)
                .and("priority").is(maxPriority);
        if (excludedCaseIds != null && !excludedCaseIds.isEmpty()) {
            match.and("caseId").nin(excludedCaseIds);
        }
        if (excludedUsers != null && !excludedUsers.isEmpty()) {
            match.and("userEmail").nin(excludedUsers);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(match),
                context -> new Document("$setWindowFields", new Document()
                        .append("partitionBy", "$userEmail")
                        .append("sortBy", new Document("createdAt", 1))
                        .append("output", new Document("userFirstTask",
                                new Document("$min", "$createdAt")
                                        .append("window", new Document("documents",
                                                Arrays.asList("unbounded", "unbounded")))))),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "createdAt")),
                context -> new Document("$group", new Document()
                        .append("_id", new Document("userEmail", "$userEmail")
                                .append("caseId", "$caseId"))
                        .append("doc", new Document("$first", "$$ROOT"))),
                Aggregation.replaceRoot("doc")
        );

        // читаем как Document, чтобы достать userFirstTask (в TaskQueue такого поля нет)
        List<Document> candidateDocs = new ArrayList<>(mongoTemplate
                .aggregate(aggregation, "task_queue", Document.class)
                .getMappedResults());

        if (candidateDocs.isEmpty()) return null;

        // время первой задачи каждого юзера — для тай-брейка "кто раньше начал грузить"
        Map<String, Date> userFirstTask = new HashMap<>();
        for (Document d : candidateDocs) {
            userFirstTask.putIfAbsent(d.getString("userEmail"), d.getDate("userFirstTask"));
        }

        // 3) сортировка: сначала дело с наименьшим прогрессом (дела чередуются),
        //    при равном прогрессе — юзер, который раньше начал грузить, затем createdAt
        candidateDocs.sort(Comparator
                .comparingInt((Document d) ->
                        sentByCase.getOrDefault(((Number) d.get("caseId")).longValue(), 0))
                .thenComparing(d -> userFirstTask.get(d.getString("userEmail")))
                .thenComparing(d -> d.getDate("createdAt")));

        Document top = candidateDocs.get(0);
        Long caseId = ((Number) top.get("caseId")).longValue();

        TaskQueue task = mongoTemplate.getConverter().read(TaskQueue.class, top);

        log.info("Selected task {} for user {} (caseId={}, sent={}, userFirstTask={}, priority={})",
                task.getFileName(), task.getUserEmail(), caseId,
                sentByCase.getOrDefault(caseId, 0),
                userFirstTask.get(task.getUserEmail()), maxPriority);
        return task;
    }
    public List<String> getProcessingUserEmails() {
        Query query = new Query(Criteria.where("status").is(TaskStatus.PROCESSING));
        return mongoTemplate.findDistinct(query, "userEmail", TaskQueue.class, String.class);
    }

    public boolean markAsSentToProcessing(Long caseFileId) {
        Query q = new Query(Criteria.where("caseFileId").is(caseFileId)
                .and("status").is(TaskStatus.PENDING));
        Update u = new Update()
                        .set("status", TaskStatus.PROCESSING)
                        .set("sentToQueueAt", LocalDateTime.now());
        var res = mongoTemplate.updateFirst(q, u, TaskQueue.class);
        return res.getModifiedCount() > 0;
    }

    private int getMaxPendingPriority(List<Long> excludedCaseIds, List<String> excludedUsers) {
        Criteria criteria = Criteria.where("status").is(TaskStatus.PENDING);
        if (excludedCaseIds != null && !excludedCaseIds.isEmpty()) {
            criteria.and("caseId").nin(excludedCaseIds);
        }
        if (excludedUsers != null && !excludedUsers.isEmpty()) {
            criteria.and("userEmail").nin(excludedUsers);
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "priority"))
                .limit(1);
        TaskQueue top = mongoTemplate.findOne(query, TaskQueue.class);
        return top != null ? top.getPriority() : 0;
    }

    public void completeTask(Long caseFileId, Long processingDurationSeconds) {
        List<TaskQueue> tasks = taskQueueRepository
                .findByCaseFileIdAndStatus(caseFileId, TaskStatus.PROCESSING);

        if (!tasks.isEmpty()) {
            TaskQueue task = tasks.get(0);
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setProcessingDurationSeconds(processingDurationSeconds);
            taskQueueRepository.save(task);
            log.info("Task {} completed for caseFile {}", task.getId(), caseFileId);
        } else {
            log.warn("No PROCESSING task found for caseFileId {}", caseFileId);
        }
    }
    public void failTask(Long caseFileId, String errorMessage) {
        List<TaskQueue> tasks = taskQueueRepository
                .findByCaseFileIdAndStatus(caseFileId, TaskStatus.PROCESSING);

        if (!tasks.isEmpty()) {
            TaskQueue task = tasks.get(0);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(errorMessage);
            task.setCompletedAt(LocalDateTime.now());
            taskQueueRepository.save(task);
            log.error("Task {} failed for caseFile {}: {}", task.getId(), caseFileId, errorMessage);
        } else {
            log.warn("No PROCESSING task found for caseFileId {}", caseFileId);
        }
    }

    public void deleteTask(Long caseFileId) {
        taskQueueRepository.deleteByCaseFileId(caseFileId);
    }

    public void deleteTasksByCaseId(Long caseId){ taskQueueRepository.deleteByCaseId(caseId);}
    public Long getProcessingTasksCount() {
        return taskQueueRepository.countByStatus(TaskStatus.PROCESSING);
    }

    public List<Long> getProcessingCaseIds() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(TaskStatus.PROCESSING));
        return mongoTemplate.findDistinct(query, "caseId", TaskQueue.class, Long.class);
    }
    public int getCasePriority(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            return 0;
        }
        if (!caseEntity.isStatus()) {
            return -1;
        }
        if (caseEntity.getPriority() != null) {
            return caseEntity.getPriority();
        }
        return taskQueueRepository
                .findByCaseId(caseId)
                .stream()
                .filter(task -> task.getStatus() == TaskStatus.PENDING)
                .map(TaskQueue::getPriority)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    public List<Long> findOrphanedCaseFileIds() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(orphanMinAgeMinutes);
        Query q = new Query(Criteria.where("createdAt").lt(cutoff));

        List<Long> mongoIds = mongoTemplate.findDistinct(
                q, "caseFileId", TaskQueue.class, Long.class);

        if (mongoIds.isEmpty()) return List.of();

        Set<Long> existing = new HashSet<>(caseFileRepository.findExistingIds(mongoIds));

        return mongoIds.stream()
                .filter(id -> id != null && !existing.contains(id))
                .toList();
    }
    public OrphanCleanupResult reconcileOrphanedTasks(boolean dryRun) {
        List<Long> orphaned = findOrphanedCaseFileIds();

        if (orphaned.isEmpty()) {
            return new OrphanCleanupResult(0, 0, List.of(), dryRun);
        }

        long deleted = 0;
        if (!dryRun) {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(orphanMinAgeMinutes);
            Query q = new Query(Criteria.where("caseFileId").in(orphaned)
                    .and("createdAt").lt(cutoff));
            deleted = mongoTemplate.remove(q, TaskQueue.class).getDeletedCount();
            log.warn("Reconciliation: removed {} orphaned tasks, caseFileIds={}", deleted, orphaned);
        } else {
            log.info("Reconciliation DRY-RUN: {} orphaned tasks, caseFileIds={}", orphaned.size(), orphaned);
        }

        return new OrphanCleanupResult(orphaned.size(), deleted, orphaned, dryRun);
    }
    public record OrphanCleanupResult(
            int orphanedFound,
            long deleted,
            List<Long> orphanedCaseFileIds,
            boolean dryRun
    ) {}
}