package org.di.digital.service.auth;

import org.di.digital.dto.request.auth.*;
import org.di.digital.dto.response.auth.JwtResponse;

public interface AuthService {
    String signupAlisher(SignUpRequest request);
    String signupRegAdmin(SignUpRequest request);
    String signup(SignUpRequest request);
    JwtResponse login(LoginRequest request);
    String forgotPassword(ForgotPasswordRequest request);
    String resetPassword(ResetPasswordRequest request);
    JwtResponse refreshToken(RefreshTokenRequest request);
}
