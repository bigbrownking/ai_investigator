package org.di.digital.dto.message;

import lombok.*;
import org.di.digital.dto.response.osmotr.OsmotrReportResponse;
import org.di.digital.model.enums.OsmotrProcessingStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrResultMessage {
    private Long fileId;
    private String sessionId;
    private String caseNumber;
    private String fileName;
    private String userEmail;
    private OsmotrProcessingStatus status;
    private OsmotrReportResponse result;
    private String errorMessage;
    private LocalDateTime timestamp;
    private Long processingDurationSeconds;
}