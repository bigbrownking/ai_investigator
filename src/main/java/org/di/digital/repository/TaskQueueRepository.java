package org.di.digital.repository;

import org.di.digital.model.TaskQueue;
import org.di.digital.model.TaskStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskQueueRepository extends MongoRepository<TaskQueue, String> {
    List<TaskQueue> findByStatusOrderByCreatedAtAsc(TaskStatus status);
    List<TaskQueue> findByUserEmailAndStatus(String userEmail, TaskStatus status);
    Long countByStatus(TaskStatus status);
    void deleteByStatusAndCompletedAtBefore(TaskStatus status, LocalDateTime dateTime);
    void deleteByCaseFileId(long caseFileId);
    List<TaskQueue> findByCaseFileId(Long caseFileId);
}