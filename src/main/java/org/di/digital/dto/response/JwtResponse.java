package org.di.digital.dto.response;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class JwtResponse {
    private String token;
    private String refreshToken;
    private String type;
    private String username;
}
