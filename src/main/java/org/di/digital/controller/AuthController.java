package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.LoginRequest;
import org.di.digital.dto.request.RefreshTokenRequest;
import org.di.digital.dto.request.SignUpRequest;
import org.di.digital.dto.response.JwtResponse;
import org.di.digital.service.AuthService;
import org.di.digital.service.FLService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/auth/")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/SIGNUP")
    public ResponseEntity<String> SIGNUP(@RequestBody SignUpRequest signUpRequest) {
        return ResponseEntity.ok(authService.signupAlisher(signUpRequest));
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignUpRequest signUpRequest) {
        return ResponseEntity.ok(authService.signup(signUpRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody RefreshTokenRequest refreshTokenRequest){
        return ResponseEntity.ok(authService.refreshToken(refreshTokenRequest));
    }
}
