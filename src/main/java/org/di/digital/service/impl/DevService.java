package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.TaskQueue;
import org.di.digital.model.enums.TaskStatus;
import org.di.digital.repository.TaskQueueRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.bson.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevService {

    private final TaskQueueRepository taskQueueRepository;
    private final MongoTemplate mongoTemplate;

    // ─── Priority ────────────────────────────────────────────────

    public void setCasePriority(String caseNumber, int priority) {
        Query query = new Query(
                Criteria.where("caseNumber").is(caseNumber)
                        .and("status").is(TaskStatus.PENDING)
        );
        Update update = new Update().set("priority", priority);
        long modified = mongoTemplate.updateMulti(query, update, TaskQueue.class).getModifiedCount();
        log.info("Set priority {} for case {}: {} tasks updated", priority, caseNumber, modified);
    }

    public void setFilePriority(Long caseFileId, int priority) {
        Query query = new Query(Criteria.where("caseFileId").is(caseFileId));
        Update update = new Update().set("priority", priority);
        mongoTemplate.updateFirst(query, update, TaskQueue.class);
        log.info("Set priority {} for caseFileId {}", priority, caseFileId);
    }

    // ─── Queue state ──────────────────────────────────────────────

    public List<TaskQueue> getTasksByStatus(TaskStatus status) {
        return taskQueueRepository.findByStatus(status);
    }

    public List<TaskQueue> getProcessingTasks() {
        return taskQueueRepository.findByStatus(TaskStatus.PROCESSING);
    }

    public List<TaskQueue> getPendingTasks() {
        return taskQueueRepository.findByStatus(TaskStatus.PENDING);
    }

    public List<TaskQueue> getTasksByCase(String caseNumber) {
        Query query = new Query(Criteria.where("caseNumber").is(caseNumber));
        return mongoTemplate.find(query, TaskQueue.class);
    }

    public List<TaskQueue> getTasksByUser(String userEmail) {
        Query query = new Query(Criteria.where("userEmail").is(userEmail));
        return mongoTemplate.find(query, TaskQueue.class);
    }

    // ─── Statistics ───────────────────────────────────────────────

    public QueueStatsResponse getQueueStats() {
        long pending    = taskQueueRepository.countByStatus(TaskStatus.PENDING);
        long processing = taskQueueRepository.countByStatus(TaskStatus.PROCESSING);
        long completed  = taskQueueRepository.countByStatus(TaskStatus.COMPLETED);
        long failed     = taskQueueRepository.countByStatus(TaskStatus.FAILED);

        // tasks per user
        Aggregation byUser = Aggregation.newAggregation(
                Aggregation.group("userEmail").count().as("count")
        );
        AggregationResults<Document> userResults =
                mongoTemplate.aggregate(byUser, "task_queue", Document.class);
        Map<String, Integer> perUser = userResults.getMappedResults().stream()
                .collect(Collectors.toMap(
                        d -> d.getString("_id"),
                        d -> d.getInteger("count")
                ));

        // tasks per case
        Aggregation byCase = Aggregation.newAggregation(
                Aggregation.group("caseNumber").count().as("count")
        );
        AggregationResults<Document> caseResults =
                mongoTemplate.aggregate(byCase, "task_queue", Document.class);
        Map<String, Integer> perCase = caseResults.getMappedResults().stream()
                .collect(Collectors.toMap(
                        d -> d.getString("_id"),
                        d -> d.getInteger("count")
                ));

        // avg processing duration (completed tasks only)
        Aggregation avgDuration = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(TaskStatus.COMPLETED)
                        .and("processingDurationSeconds").exists(true)),
                Aggregation.group().avg("processingDurationSeconds").as("avg")
        );
        AggregationResults<Document> durationResults =
                mongoTemplate.aggregate(avgDuration, "task_queue", Document.class);
        Double avgSec = durationResults.getMappedResults().stream()
                .findFirst()
                .map(d -> d.getDouble("avg"))
                .orElse(0.0);

        return new QueueStatsResponse(pending, processing, completed, failed, perUser, perCase, avgSec);
    }

    // ─── Control ──────────────────────────────────────────────────

    public void retryFailedTask(Long caseFileId) {
        Query query = new Query(
                Criteria.where("caseFileId").is(caseFileId)
                        .and("status").is(TaskStatus.FAILED)
        );
        Update update = new Update()
                .set("status", TaskStatus.PENDING)
                .unset("errorMessage")
                .unset("completedAt");
        mongoTemplate.updateFirst(query, update, TaskQueue.class);
        log.info("Retrying failed task for caseFileId {}", caseFileId);
    }

    public void retryAllFailedForCase(String caseNumber) {
        Query query = new Query(
                Criteria.where("caseNumber").is(caseNumber)
                        .and("status").is(TaskStatus.FAILED)
        );
        Update update = new Update()
                .set("status", TaskStatus.PENDING)
                .unset("errorMessage")
                .unset("completedAt");
        long modified = mongoTemplate.updateMulti(query, update, TaskQueue.class).getModifiedCount();
        log.info("Retried {} failed tasks for case {}", modified, caseNumber);
    }

    public void cancelPendingTask(Long caseFileId) {
        Query query = new Query(
                Criteria.where("caseFileId").is(caseFileId)
                        .and("status").is(TaskStatus.PENDING)
        );
        Update update = new Update().set("status", TaskStatus.FAILED)
                .set("errorMessage", "Cancelled by admin");
        mongoTemplate.updateFirst(query, update, TaskQueue.class);
        log.info("Cancelled pending task for caseFileId {}", caseFileId);
    }
    public long retryAllFailed() {
        Query query = new Query(Criteria.where("status").is(TaskStatus.FAILED));
        Update update = new Update()
                .set("status", TaskStatus.PENDING)
                .unset("errorMessage")
                .unset("completedAt");
        long modified = mongoTemplate.updateMulti(query, update, TaskQueue.class).getModifiedCount();
        log.info("Retried all {} failed tasks", modified);
        return modified;
    }

    public record QueueStatsResponse(
            long pending,
            long processing,
            long completed,
            long failed,
            Map<String, Integer> tasksPerUser,
            Map<String, Integer> tasksPerCase,
            double avgProcessingSeconds
    ) {}
}