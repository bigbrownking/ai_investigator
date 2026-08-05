package org.di.digital.controller;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.client.FaceAuthClient;
import org.di.digital.dto.response.auth.JwtResponse;
import org.di.digital.model.user.User;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.di.digital.security.jwt.PreAuthTokenUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/face-id")
@RequiredArgsConstructor
public class FaceCompleteController {

    private final PreAuthTokenUtil preAuthTokenUtil;
    private final JwtTokenUtil jwtTokenUtil;
    private final FaceAuthClient faceAuthClient;
    private final UserRepository userRepository;

    @PostMapping("/face-complete")
    public ResponseEntity<?> faceComplete(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String token = authHeader.startsWith("Bearer_")
                ? authHeader.substring("Bearer_".length()) : authHeader;

        Claims claims;
        try {
            claims = preAuthTokenUtil.parse(token);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid pre-auth token"));
        }
        if (!preAuthTokenUtil.isPreAuth(claims)) {
            return ResponseEntity.status(401).body(Map.of("error", "Not a pre-auth token"));
        }

        Long userId = preAuthTokenUtil.userId(claims);
        String email = claims.getSubject();
        String scope = claims.get("scope", String.class);   // "AUTH" | "ENROLL"

        String jobId = body.get("jobId");
        if (jobId == null || jobId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "jobId is required"));
        }

        Map<String, Object> job = faceAuthClient.getJob(userId, jobId, null);
        String status = (String) job.get("status");
        String type = (String) job.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) job.get("result");

        // job ещё не завершён -> не 401 (это гонка на фронте), а 409, чтобы фронт до-поллил
        if (!"SUCCEEDED".equals(status)) {
            log.warn("face-complete: job {} not finished, status={}", jobId, status);
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Job not finished", "status", String.valueOf(status)));
        }

        boolean ok;
        if ("ENROLL".equals(scope)) {
            // тип job должен соответствовать enroll-флоу
            if (!"ENROLL_REFERENCE_SET".equals(type) && !"PREPARE_REFERENCE_SET".equals(type)) {
                log.warn("face-complete ENROLL scope but job type={} (jobId={})", type, jobId);
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "jobId is not an enroll job", "type", String.valueOf(type)));
            }
            ok = result != null && result.get("referenceSetId") != null;
            if (ok) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));
                user.setFaceEnabled(true);
                userRepository.save(user);
            }
        } else {
            // AUTH scope -> job должен быть verify
            if (!"VERIFY".equals(type)) {
                log.warn("face-complete AUTH scope but job type={} (jobId={})", type, jobId);
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "jobId is not a verify job", "type", String.valueOf(type)));
            }
            ok = result != null && Boolean.TRUE.equals(result.get("matched"));
        }

        if (!ok) {
            return ResponseEntity.status(401).body(Map.of("error", "Face check not passed"));
        }

        String access = jwtTokenUtil.generateTokenFromUsername(email);
        String refresh = jwtTokenUtil.generateRefreshToken(email);
        return ResponseEntity.ok(JwtResponse.builder()
                .token(access).refreshToken(refresh)
                .type("Bearer").username(email)
                .faceEnabled(true)
                .build());
    }
}