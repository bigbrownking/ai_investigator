package org.di.digital.service;

import org.di.digital.dto.request.LoginRequest;
import org.di.digital.dto.request.RefreshTokenRequest;
import org.di.digital.dto.request.SignUpRequest;
import org.di.digital.dto.response.JwtResponse;

public interface AuthService {
    String signup(SignUpRequest request);
    JwtResponse login(LoginRequest request);
    JwtResponse refreshToken(RefreshTokenRequest request);

}
