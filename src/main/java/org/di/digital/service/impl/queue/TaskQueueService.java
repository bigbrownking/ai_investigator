package org.di.digital.service.impl.queue;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.di.digital.model.queue.QueueState;
import org.di.digital.model.queue.TaskQueue;
import org.di.digital.model.enums.TaskStatus;
import org.di.digital.repository.queue.QueueStateRepository;
import org.di.digital.repository.queue.TaskQueueRepository;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskQueueService {

    private final TaskQueueRepository taskQueueRepository;
    private final QueueStateRepository queueStateRepository;
    private final MongoTemplate mongoTemplate;
    private final RabbitAdmin rabbitAdmin;

    @Value("${spring.rabbitmq.mediator.queue}")
    public String DOCUMENT_QUEUE;
    private static final String ROUND_ROBIN_STATE_ID = "round_robin_state";

    @PostConstruct
    public void resetStuckTasks() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(TaskStatus.PROCESSING)
                .and("sentToQueueAt").lt(LocalDateTime.now()));
        List<TaskQueue> stuckTasks = mongoTemplate.find(query, TaskQueue.class);

        if (!stuckTasks.isEmpty()) {
            stuckTasks.forEach(task -> {
                task.setStatus(TaskStatus.PENDING);
                task.setSentToQueueAt(null);
            });
            taskQueueRepository.saveAll(stuckTasks);
            log.info("Reset {} stuck PROCESSING tasks to PENDING", stuckTasks.size());
        }

        try {
            rabbitAdmin.purgeQueue(DOCUMENT_QUEUE, false);
            log.info("Purged RabbitMQ queue on startup");
        } catch (Exception e) {
            log.error("Failed to purge queue", e);
        }
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
                .priority(0)
                .build();

        taskQueueRepository.save(task);
        log.info("Added task {} to queue for user {}", fileName, userEmail);
    }

    public TaskQueue getNextTaskByRoundRobin(List<Long> excludedCaseIds) {
        int maxPriority = getMaxPendingPriority(excludedCaseIds);

        List<String> users = getOrderedUsersWithPendingTasksByPriority(maxPriority, excludedCaseIds);
        log.info("DEBUG: Max priority={}, users with this priority: {}", maxPriority, users);

        if (users.isEmpty()) return null;

        QueueState state = queueStateRepository.findById(ROUND_ROBIN_STATE_ID)
                .orElse(QueueState.builder()
                        .id(ROUND_ROBIN_STATE_ID)
                        .lastSelectedUser(null)
                        .build());

        String lastSelectedUser = state.getLastSelectedUser();

        int startIndex = 0;
        if (lastSelectedUser != null) {
            int lastIndex = users.indexOf(lastSelectedUser);
            if (lastIndex != -1) {
                startIndex = (lastIndex + 1) % users.size();
            }
        }

        for (int i = 0; i < users.size(); i++) {
            String candidate = users.get((startIndex + i) % users.size());

            Query taskQuery = new Query();
            taskQuery.addCriteria(Criteria.where("userEmail").is(candidate)
                    .and("status").is(TaskStatus.PENDING)
                    .and("priority").is(maxPriority));
            if (excludedCaseIds != null && !excludedCaseIds.isEmpty()) {
                taskQuery.addCriteria(Criteria.where("caseId").nin(excludedCaseIds));
            }
            taskQuery.with(Sort.by(Sort.Direction.ASC, "createdAt"));
            taskQuery.limit(1);

            TaskQueue task = mongoTemplate.findOne(taskQuery, TaskQueue.class);

            if (task != null) {
                task.setStatus(TaskStatus.PROCESSING);
                task.setSentToQueueAt(LocalDateTime.now());
                taskQueueRepository.save(task);

                state.setLastSelectedUser(candidate);
                queueStateRepository.save(state);

                log.info("Selected task {} for user {} (priority={})",
                        task.getFileName(), candidate, maxPriority);
                return task;
            }
        }

        return null;
    }

    private int getMaxPendingPriority(List<Long> excludedCaseIds) {
        Query query = new Query();
        Criteria criteria = Criteria.where("status").is(TaskStatus.PENDING);
        if (excludedCaseIds != null && !excludedCaseIds.isEmpty()) {
            criteria.and("caseId").nin(excludedCaseIds);
        }
        query.addCriteria(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "priority"));
        query.limit(1);
        TaskQueue top = mongoTemplate.findOne(query, TaskQueue.class);
        return top != null ? top.getPriority() : 0;
    }

    private List<String> getOrderedUsersWithPendingTasksByPriority(int priority, List<Long> excludedCaseIds) {
        Criteria matchCriteria = Criteria.where("status").is(TaskStatus.PENDING)
                .and("priority").is(priority);
        if (excludedCaseIds != null && !excludedCaseIds.isEmpty()) {
            matchCriteria.and("caseId").nin(excludedCaseIds);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group("userEmail")
                        .min("createdAt").as("firstTaskTime"),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "firstTaskTime"))
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, "task_queue", Document.class);

        return results.getMappedResults()
                .stream()
                .map(doc -> doc.getString("_id"))
                .toList();
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

    public Double getAverageProcessingDuration() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        Criteria.where("status").is(TaskStatus.COMPLETED)
                                .and("processingDurationSeconds").ne(null)
                ),
                Aggregation.group().avg("processingDurationSeconds").as("avgDuration")
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, "task_queue", Document.class);

        Document result = results.getUniqueMappedResult();
        return result != null ? result.getDouble("avgDuration") : null;
    }

    public List<Long> getProcessingCaseIds() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(TaskStatus.PROCESSING));
        return mongoTemplate.findDistinct(query, "caseId", TaskQueue.class, Long.class);
    }
}