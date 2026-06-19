package org.di.digital.dto.response.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChallengeResponse {
    private String challengeId;
}
