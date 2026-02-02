package org.di.digital.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.model.enums.CaseFileStatusEnum;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileStatusInfo {
    private Long fileId;
    private String fileName;
    private CaseFileStatusEnum status;
    private LocalDateTime uploadedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}