package org.di.digital.dto.response.auth;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.model.enums.LivenessStep;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class LivenessChallengeResponse {
    private String livenessId;
    private List<LivenessStep> steps;
    private LocalDateTime expiresAt;
}