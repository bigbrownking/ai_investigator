package org.di.digital.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.model.enums.CaseFileStatusEnum;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileProcessingNotification {
    private Long caseFileId;
    private String caseNumber;
    private String fileName;
    private CaseFileStatusEnum status;
    private String text;
    private String errorMessage;
    private LocalDateTime timestamp;
}