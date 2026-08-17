package org.di.digital.dto.request.auth;

import lombok.Data;

@Data
public class ChangeExpiredPasswordRequest {
    private String newPassword;
}