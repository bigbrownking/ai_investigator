package org.di.digital.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CaseProcessingNotification {
    private String caseNumber;
    private String caseTitle;

    private Integer totalFiles;
    private Integer pendingFiles;
    private Integer processingFiles;
    private Integer completedFiles;
    private Integer failedFiles;

    private List<FileStatusInfo> files;

    private String latestActivity;
    private Long latestFileId;
    private String latestFileName;

    private LocalDateTime timestamp;
}