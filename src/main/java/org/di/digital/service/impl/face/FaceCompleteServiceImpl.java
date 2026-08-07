package org.di.digital.service.impl.face;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.client.FaceAuthClient;
import org.di.digital.dto.response.auth.JwtResponse;
import org.di.digital.exception.FaceCompleteException;
import org.di.digital.exception.FaceErrorMessages;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.user.User;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.security.jwt.JwtTokenUtil;
import org.di.digital.security.jwt.PreAuthTokenUtil;
import org.di.digital.service.face.FaceCompleteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceCompleteServiceImpl implements FaceCompleteService {

    private final PreAuthTokenUtil preAuthTokenUtil;
    private final JwtTokenUtil jwtTokenUtil;
    private final FaceAuthClient faceAuthClient;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public JwtResponse complete(String authHeader, String jobId) {
        Claims claims = parsePreAuth(authHeader);

        Long userId = preAuthTokenUtil.userId(claims);
        String email = claims.getSubject();
        String scope = claims.get("scope", String.class);

        if (jobId == null || jobId.isBlank()) {
            throw new FaceCompleteException(HttpStatus.BAD_REQUEST, "jobId is required");
        }

        Map<String, Object> job = faceAuthClient.getJob(userId, jobId, null);
        String status = (String) job.get("status");
        String type = (String) job.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) job.get("result");

        verifyJobFinished(job, jobId, status);

        boolean ok = "ENROLL".equals(scope)
                ? handleEnroll(userId, jobId, type, result)
                : handleVerify(jobId, type, result);

        if (!ok) {
            throw new FaceCompleteException(
                    HttpStatus.UNAUTHORIZED, "Face check not passed", "code", "MATCH_FAILED");
        }

        return JwtResponse.builder()
                .token(jwtTokenUtil.generateTokenFromUsername(email))
                .refreshToken(jwtTokenUtil.generateRefreshToken(email))
                .type("Bearer")
                .username(email)
                .faceEnabled(true)
                .build();
    }

    private Claims parsePreAuth(String authHeader) {
        String token = authHeader.startsWith("Bearer_")
                ? authHeader.substring("Bearer_".length())
                : authHeader;

        Claims claims;
        try {
            claims = preAuthTokenUtil.parse(token);
        } catch (Exception e) {
            throw new FaceCompleteException(HttpStatus.UNAUTHORIZED, "Invalid pre-auth token");
        }
        if (!preAuthTokenUtil.isPreAuth(claims)) {
            throw new FaceCompleteException(HttpStatus.UNAUTHORIZED, "Not a pre-auth token");
        }
        return claims;
    }

    private void verifyJobFinished(Map<String, Object> job, String jobId, String status) {
        if ("FAILED".equals(status) || "REJECTED".equals(status)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) job.get("error");
            String code = error != null ? (String) error.get("code") : null;
            log.warn("face-complete: job {} failed, code={}", jobId, code);
            throw new FaceCompleteException(
                    HttpStatus.UNPROCESSABLE_ENTITY, FaceErrorMessages.map(code),
                    "code", code != null ? code : "UNKNOWN");
        }
        if (!"SUCCEEDED".equals(status)) {
            log.warn("face-complete: job {} not finished, status={}", jobId, status);
            throw new FaceCompleteException(
                    HttpStatus.CONFLICT, "Job not finished", "status", String.valueOf(status));
        }
    }

    private boolean handleEnroll(Long userId, String jobId, String type,
                                 Map<String, Object> result) {
        if (!"ENROLL_REFERENCE_SET".equals(type) && !"PREPARE_REFERENCE_SET".equals(type)) {
            log.warn("face-complete ENROLL scope but job type={} (jobId={})", type, jobId);
            throw new FaceCompleteException(
                    HttpStatus.BAD_REQUEST, "jobId is not an enroll job", "type", String.valueOf(type));
        }
        boolean ok = result != null && result.get("referenceSetId") != null;
        if (ok) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
            user.setFaceEnabled(true);
            userRepository.save(user);
        }
        return ok;
    }

    private boolean handleVerify(String jobId, String type, Map<String, Object> result) {
        if (!"VERIFY".equals(type)) {
            log.warn("face-complete AUTH scope but job type={} (jobId={})", type, jobId);
            throw new FaceCompleteException(
                    HttpStatus.BAD_REQUEST, "jobId is not a verify job", "type", String.valueOf(type));
        }
        return result != null && Boolean.TRUE.equals(result.get("matched"));
    }
}