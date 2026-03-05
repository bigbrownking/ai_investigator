package org.di.digital.service.impl.queue;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.di.digital.config.RabbitMQConfig;
import org.di.digital.model.QueueState;
import org.di.digital.model.TaskQueue;
import org.di.digital.model.TaskStatus;
import org.di.digital.repository.QueueStateRepository;
import org.di.digital.repository.TaskQueueRepository;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
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

    private static final String ROUND_ROBIN_STATE_ID = "round_robin_state";

    @PostConstruct
    public void resetStuckTasks() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(TaskStatus.PROCESSING));
        List<TaskQueue> stuckTasks = mongoTemplate.find(query, TaskQueue.class);

        if (!stuckTasks.isEmpty()) {
            stuckTasks.forEach(task -> {
                task.setStatus(TaskStatus.PENDING);
                task.setSentToQueueAt(null);
            });
            taskQueueRepository.saveAll(stuckTasks);
            log.info("Reset {} stuck PROCESSING tasks to PENDING", stuckTasks.size());
        }

        rabbitAdmin.purgeQueue(RabbitMQConfig.DOCUMENT_QUEUE, false);
        log.info("Purged RabbitMQ queue on startup");
    }

    public void addTaskToQueue(String userEmail, Long caseId, String caseNumber,
                               String fileName, String fileUrl, Long caseFileId) {
        TaskQueue task = TaskQueue.builder()
                .userEmail(userEmail)
                .caseFileId(caseFileId)
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

    public TaskQueue getNextTaskByRoundRobin() {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(TaskStatus.PENDING));

        List<String> users = getOrderedUsersWithPendingTasks();

        if (users.isEmpty()) {
            log.info("No pending tasks found");
            return null;
        }

        QueueState state = queueStateRepository.findById(ROUND_ROBIN_STATE_ID)
                .orElse(QueueState.builder()
                        .id(ROUND_ROBIN_STATE_ID)
                        .lastSelectedUser(null)
                        .build());

        String lastSelectedUser = state.getLastSelectedUser();

        // Определяем стартовый индекс — следующий после последнего выбранного
        int startIndex = 0;
        if (lastSelectedUser != null) {
            int lastIndex = users.indexOf(lastSelectedUser);
            if (lastIndex != -1) {
                startIndex = (lastIndex + 1) % users.size();
            }
        }

        // Перебираем пользователей начиная со startIndex
        for (int i = 0; i < users.size(); i++) {
            String candidate = users.get((startIndex + i) % users.size());

            List<TaskQueue> userTasks = taskQueueRepository
                    .findByUserEmailAndStatus(candidate, TaskStatus.PENDING);

            if (!userTasks.isEmpty()) {
                TaskQueue task = userTasks.get(0);
                task.setStatus(TaskStatus.PROCESSING);
                task.setSentToQueueAt(LocalDateTime.now());
                taskQueueRepository.save(task);

                // Сохраняем выбранного пользователя в БД
                state.setLastSelectedUser(candidate);
                queueStateRepository.save(state);

                log.info("Selected task {} for user {} by Round-Robin",
                        task.getFileName(), candidate);
                return task;
            }
        }

        return null;
    }

    public void completeTask(Long caseFileId) {
        List<TaskQueue> tasks = taskQueueRepository.findByCaseFileId(caseFileId);

        if (!tasks.isEmpty()) {
            TaskQueue task = tasks.get(0);
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            taskQueueRepository.save(task);

            log.info("Task {} completed for caseFile {}", task.getId(), caseFileId);
        } else {
            log.warn("No task found for caseFileId {}", caseFileId);
        }
    }

    private List<String> getOrderedUsersWithPendingTasks() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(TaskStatus.PENDING)),
                Aggregation.group("userEmail")
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, "task_queue", Document.class);

        return results.getMappedResults()
                .stream()
                .map(doc -> doc.getString("_id"))
                .toList();
    }
    public void failTask(Long caseFileId, String errorMessage) {
        List<TaskQueue> tasks = taskQueueRepository.findByCaseFileId(caseFileId);

        if (!tasks.isEmpty()) {
            TaskQueue task = tasks.get(0);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(errorMessage);
            task.setCompletedAt(LocalDateTime.now());
            taskQueueRepository.save(task);

            log.error("Task {} failed for caseFile {}: {}",
                    task.getId(), caseFileId, errorMessage);
        } else {
            log.warn("No task found for caseFileId {}", caseFileId);
        }
    }

    public void deleteTask(Long caseFileId) {
        taskQueueRepository.deleteByCaseFileId(caseFileId);
    }

    public List<TaskQueue> getUserTasks(String userEmail) {
        return taskQueueRepository.findByUserEmailAndStatus(userEmail, TaskStatus.PENDING);
    }

    public Long getPendingTasksCount() {
        return taskQueueRepository.countByStatus(TaskStatus.PENDING);
    }
    public Long getProcessingTasksCount() {
        return taskQueueRepository.countByStatus(TaskStatus.PROCESSING);
    }
}