package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.model.enums.PlanStatus;

import java.time.LocalDateTime;

@Getter
@Builder
public class PlanSubmitResponse {
    private PlanStatus planStatus;
    private LocalDateTime planSubmittedAt;
    private boolean canWithdraw;
}
