package org.di.digital.dto.response.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LivenessVerifyResponse {
    private boolean success;
    private String livenessToken;
}
