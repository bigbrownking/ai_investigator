package org.di.digital.controller;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.di.digital.client.FaceAuthClient;
import org.di.digital.dto.response.auth.JwtResponse;
import org.di.digital.model.user.User;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.di.digital.security.jwt.PreAuthTokenUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class FaceCompleteController {

    private final PreAuthTokenUtil preAuthTokenUtil;
    private final JwtTokenUtil jwtTokenUtil;
    private final FaceAuthClient faceAuthClient;
    private final UserRepository userRepository;

    @PostMapping("/face-complete")
    public ResponseEntity<JwtResponse> faceComplete(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String token = authHeader.startsWith("Bearer_")
                ? authHeader.substring("Bearer_".length()) : authHeader;
        Claims claims = preAuthTokenUtil.parse(token);
        if (!preAuthTokenUtil.isPreAuth(claims)) return ResponseEntity.status(401).build();

        Long userId = preAuthTokenUtil.userId(claims);
        String email = claims.getSubject();
        String scope = claims.get("scope", String.class);   // "AUTH" | "ENROLL"

        String jobId = body.get("jobId");
        Map<String, Object> job = faceAuthClient.getJob(userId, jobId, null);
        String status = (String) job.get("status");
        Map<String, Object> result = (Map<String, Object>) job.get("result");

        boolean ok;
        if ("ENROLL".equals(scope)) {
            // сценарий 2: job — это enroll/reference-set, успех = prepared/referenceSetId
            ok = "SUCCEEDED".equals(status) && result != null
                    && result.get("referenceSetId") != null;
            if (ok) {
                // проставляем флаг faceEnabled в оркестраторе
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));
                user.setFaceEnabled(true);
                userRepository.save(user);
            }
        } else {
            // сценарий 3: job — verify, успех = matched
            ok = "SUCCEEDED".equals(status) && result != null
                    && Boolean.TRUE.equals(result.get("matched"));
        }

        if (!ok) return ResponseEntity.status(401).build();

        String access = jwtTokenUtil.generateTokenFromUsername(email);
        String refresh = jwtTokenUtil.generateRefreshToken(email);
        return ResponseEntity.ok(JwtResponse.builder()
                .token(access).accessToken(access).refreshToken(refresh)
                .type("Bearer").username(email)
                .faceEnabled(true)
                .build());
    }
}