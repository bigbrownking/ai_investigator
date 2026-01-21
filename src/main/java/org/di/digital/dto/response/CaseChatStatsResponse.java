package org.di.digital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseChatStatsResponse {
    private String caseNumber;
    private Long caseId;
    private Integer totalMessages;
    private Boolean hasHistory;
    private LocalDateTime lastMessageAt;
}
