package org.di.digital.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResultMessage {
    private String caseNumber;
    private String fileName;
    private String userEmail;
    private ReportProcessingStatus status;
    private String reportFileUrl;
    private String errorMessage;
    private LocalDateTime timestamp;
    private long processingDurationSeconds;
}