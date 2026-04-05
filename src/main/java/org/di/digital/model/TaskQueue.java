package org.di.digital.model;

import lombok.*;
import org.di.digital.model.enums.TaskStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_queue")
@CompoundIndex(
        name = "unique_active_task",
        def = "{'caseFileId': 1, 'status': 1}"
)
public class TaskQueue {
    @Id
    private String id;

    private String userEmail;
    private Long caseId;
    private Long caseFileId;
    private String caseNumber;
    private String fileName;
    private String fileUrl;

    private TaskStatus status;
    private Integer priority;

    private LocalDateTime createdAt;
    private LocalDateTime sentToQueueAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastHeartbeatAt;

    private String errorMessage;
    private Long processingDurationSeconds;
}