package org.di.digital.controller;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.di.digital.client.FaceAuthClient;
import org.di.digital.security.jwt.PreAuthTokenUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/face-id")
@RequiredArgsConstructor
public class FaceAuthProxyController {

    private final FaceAuthClient faceAuth;
    private final PreAuthTokenUtil preAuthTokenUtil;

    // resolve userId from Bearer_<preAuthToken>
    private Long userId(String authHeader) {
        String token = authHeader.startsWith("Bearer_")
                ? authHeader.substring("Bearer_".length()) : authHeader;
        Claims c = preAuthTokenUtil.parse(token);
        return preAuthTokenUtil.userId(c);
    }

    @PostMapping("/challenges")
    public ResponseEntity<Map> challenge(
            @RequestHeader("Authorization") String auth,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(faceAuth.createChallenge(body.get("mode"), userId(auth)));
    }

    @PostMapping(value = "/jobs/verify", consumes = "multipart/form-data")
    public ResponseEntity<Map> verify(
            @RequestHeader("Authorization") String auth,
            @RequestPart("metadata") String metadata,
            @RequestPart("HOLD_FRONT") MultipartFile front,
            @RequestPart("frame") MultipartFile frame) throws Exception {
        return ResponseEntity.ok(faceAuth.verify(userId(auth), metadata, front, frame));
    }

    @PostMapping(value = "/jobs/enroll-set", consumes = "multipart/form-data")
    public ResponseEntity<Map> enrollSet(
            @RequestHeader("Authorization") String auth,
            @RequestPart("metadata") String metadata,
            @RequestPart("HOLD_FRONT") MultipartFile front,
            @RequestPart("TURN_LEFT") MultipartFile left,
            @RequestPart("TURN_RIGHT") MultipartFile right) throws Exception {
        return ResponseEntity.ok(faceAuth.enrollSet(userId(auth), metadata, front, left, right));
    }

    @PostMapping(value = "/jobs/reference-set", consumes = "multipart/form-data")
    public ResponseEntity<Map> referenceSet(
            @RequestPart("metadata") String metadata,
            @RequestPart("HOLD_FRONT") MultipartFile front,
            @RequestPart("TURN_LEFT") MultipartFile left,
            @RequestPart("TURN_RIGHT") MultipartFile right) throws Exception {
        // anonymous — registration, no userId yet
        return ResponseEntity.ok(faceAuth.referenceSet(metadata, front, left, right));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map> getJob(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestHeader(value = "X-Face-Job-Token", required = false) String jobToken,
            @PathVariable String jobId) {
        Long uid = (auth != null) ? userId(auth) : null;
        return ResponseEntity.ok(faceAuth.getJob(uid, jobId, jobToken));
    }
}