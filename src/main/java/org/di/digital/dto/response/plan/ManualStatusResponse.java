package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ManualStatusResponse {
    private boolean success;
    private String caseNumber;
    private int actionNumber;
    private String oldStatus;
    private String newStatus;
    private boolean locked;
    private String message;
    private List<Map<String, Object>> historyStatuses;

}
