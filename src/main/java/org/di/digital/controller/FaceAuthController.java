package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.auth.*;
import org.di.digital.dto.response.auth.JwtResponse;
import org.di.digital.dto.response.auth.ChallengeResponse;
import org.di.digital.dto.response.auth.FaceStatusResponse;
import org.di.digital.dto.response.auth.LivenessChallengeResponse;
import org.di.digital.dto.response.auth.LivenessVerifyResponse;
import org.di.digital.service.auth.FaceAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth/face")
@RequiredArgsConstructor
public class FaceAuthController {

    private final FaceAuthService faceAuthService;

    @PostMapping("/challenge")
    public ResponseEntity<ChallengeResponse> challenge(@RequestBody FaceChallengeRequest request) {
        return ResponseEntity.ok(faceAuthService.generateChallenge(request.getIin()));
    }

    @PostMapping("/enroll")
    public ResponseEntity<String> enroll(
            @RequestBody FaceEnrollRequest request,
            Authentication authentication) {
        faceAuthService.enroll(
                authentication.getName(),
                request.getDescriptor(),
                request.getLivenessToken()
        );
        return ResponseEntity.ok("Face ID успешно добавлен");
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody FaceLoginRequest request) {
        return ResponseEntity.ok(faceAuthService.faceLogin(request));
    }

    @DeleteMapping("/remove")
    public ResponseEntity<String> remove(Authentication authentication) {
        faceAuthService.removeFace(authentication.getName());
        return ResponseEntity.ok("Face ID удалён");
    }
    @GetMapping("/status")
    public ResponseEntity<FaceStatusResponse> status(Authentication authentication) {
        return ResponseEntity.ok(faceAuthService.getFaceStatus(authentication.getName()));
    }

    @PostMapping("/liveness/challenge")
    public ResponseEntity<LivenessChallengeResponse> livenessChallenge(
            @RequestBody LivenessChallengeRequest request) {
        return ResponseEntity.ok(faceAuthService.generateLivenessChallenge(request.getIin()));
    }

    @PostMapping("/liveness/verify")
    public ResponseEntity<LivenessVerifyResponse> livenessVerify(
            @RequestBody LivenessVerifyRequest request) {
        return ResponseEntity.ok(faceAuthService.verifyLiveness(request));
    }
}