package org.di.digital.dto.request.auth;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
    private String origin;
}
