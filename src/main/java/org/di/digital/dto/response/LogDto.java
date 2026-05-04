package org.di.digital.dto.response;

import lombok.Builder;
import lombok.Data;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;

import java.time.LocalDateTime;

@Data
@Builder
public class LogDto {
    private Long id;
    private LocalDateTime timestamp;
    private String level;
    private String action;
    private String description;
    private String caseNumber;
    private String email;
    private String ipAddress;
}
