package org.di.digital.service.impl.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.TaskQueue;
import org.di.digital.model.TaskStatus;
import org.di.digital.repository.TaskQueueRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private final MongoTemplate mongoTemplate;

    private int roundRobinIndex = 0;

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
        query.fields().include("userEmail");

        List<String> users = mongoTemplate.findDistinct(query, "userEmail",
                TaskQueue.class, String.class);

        if (users.isEmpty()) {
            return null;
        }

        String selectedUser = users.get(roundRobinIndex % users.size());
        roundRobinIndex = (roundRobinIndex + 1) % users.size();

        List<TaskQueue> userTasks = taskQueueRepository
                .findByUserEmailAndStatus(selectedUser, TaskStatus.PENDING);

        if (!userTasks.isEmpty()) {
            TaskQueue task = userTasks.get(0);

            task.setStatus(TaskStatus.PROCESSING);
            task.setSentToQueueAt(LocalDateTime.now());
            taskQueueRepository.save(task);
            log.info("Selected task {} for user {} by Round-Robin",
                    task.getFileName(), selectedUser);
            return task;
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

}