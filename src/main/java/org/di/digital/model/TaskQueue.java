package org.di.digital.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_queue")
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

    private String errorMessage;
}