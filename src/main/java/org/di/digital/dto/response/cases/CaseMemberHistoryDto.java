package org.di.digital.dto.response.cases;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CaseMemberHistoryDto {
    private String action;
    private String targetFio;
    private String targetEmail;
    private String performedByFio;
    private String performedByEmail;
    private LocalDateTime timestamp;
}