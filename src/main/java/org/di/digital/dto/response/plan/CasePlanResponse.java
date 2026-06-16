package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.model.enums.PlanStatus;

import java.util.Map;

@Getter
@Builder
public class CasePlanResponse {
    private boolean canWithdraw;
    private String approvedBy;
    private String reviewedBy;
    private PlanStatus planStatus;
    private Map<String, Object> plan;
}
