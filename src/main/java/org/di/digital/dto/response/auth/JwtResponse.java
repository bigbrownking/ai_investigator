package org.di.digital.dto.response.auth;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class JwtResponse {
    private String token;
    private String refreshToken;
    private String type;
    private String username;
    private boolean faceEnrollmentRequired;
    private boolean faceEnabled;
    private boolean requiresFaceId;
    private String preAuthToken;
    private boolean passwordExpired;
}