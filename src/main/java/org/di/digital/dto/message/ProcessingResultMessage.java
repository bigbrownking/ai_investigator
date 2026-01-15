package org.di.digital.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessingResultMessage {
    private Long caseFileId;
    private String caseNumber;
    private String fileName;
    private String userEmail;
    private ProcessingStatus status;
    private String result;
    private String errorMessage;
    private LocalDateTime timestamp;

    // Дополнительные поля для отладки
    private Long processingDurationSeconds;
}