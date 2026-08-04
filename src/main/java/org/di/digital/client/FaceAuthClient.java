package org.di.digital.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class FaceAuthClient {

    private final RestClient restClient;

    public FaceAuthClient(@Value("${faceauth.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ---- challenges ----
    public Map<String, Object> createChallenge(String mode, Long userId) {
        return restClient.post()
                .uri("/face-id/challenges")
                .header("X-User-Id", String.valueOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mode", mode))
                .retrieve()
                .body(Map.class);
    }

    // ---- reference-set (anonymous during registration; no userId yet) ----
    public Map<String, Object> referenceSet(String metadata, MultipartFile front,
                                            MultipartFile left, MultipartFile right) throws IOException {
        return restClient.post()
                .uri("/face-id/jobs/reference-set")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(threeFrameBody(metadata, front, left, right))
                .retrieve()
                .body(Map.class);
    }

    // ---- enroll-set (authenticated) ----
    public Map<String, Object> enrollSet(Long userId, String metadata, MultipartFile front,
                                         MultipartFile left, MultipartFile right) throws IOException {
        return restClient.post()
                .uri("/face-id/jobs/enroll-set")
                .header("X-User-Id", String.valueOf(userId))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(threeFrameBody(metadata, front, left, right))
                .retrieve()
                .body(Map.class);
    }
    // ---- pose-check (превью позы, без job) ----
    public Map<String, Object> poseCheck(String requestedAction, MultipartFile frame) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("frame", resourcePart(frame));
        return restClient.post()
                .uri("/face-id/test/pose-check?requestedAction={action}", requestedAction)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    // ---- verify (authenticated or pre-auth userId) ----
    public Map<String, Object> verify(Long userId, String metadata,
                                      MultipartFile front, MultipartFile frame) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("metadata", metadata);
        body.add("HOLD_FRONT", resourcePart(front));
        body.add("frame", resourcePart(frame));
        return restClient.post()
                .uri("/face-id/jobs/verify")
                .header("X-User-Id", String.valueOf(userId))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    // ---- job polling ----
    public Map<String, Object> getJob(Long userId, String jobId, String jobToken) {
        var spec = restClient.get().uri("/face-id/jobs/{id}", jobId);
        if (userId != null) spec = spec.header("X-User-Id", String.valueOf(userId));
        if (jobToken != null) spec = spec.header("X-Face-Job-Token", jobToken);
        return spec.retrieve().body(Map.class);
    }

    // ---- enrolled check (for faceEnabled at login) ----
    public boolean isEnrolled(Long userId) {
        Map resp = restClient.get()
                .uri("/face-id/enrolled/{userId}", userId)
                .header("X-User-Id", String.valueOf(userId))
                .retrieve()
                .body(Map.class);
        return resp != null && Boolean.TRUE.equals(resp.get("enrolled"));
    }

    // ---- helpers ----
    private MultiValueMap<String, Object> threeFrameBody(String metadata, MultipartFile front,
                                                         MultipartFile left, MultipartFile right) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("metadata", metadata);
        body.add("HOLD_FRONT", resourcePart(front));
        body.add("TURN_LEFT", resourcePart(left));
        body.add("TURN_RIGHT", resourcePart(right));
        return body;
    }

    private ByteArrayResource resourcePart(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        return new ByteArrayResource(bytes) {
            @Override public String getFilename() {
                return file.getOriginalFilename() == null ? "frame.jpg" : file.getOriginalFilename();
            }
        };
    }
    public Map<String, Object> adopt(String jobId, String jobToken, Long userId) {
        return restClient.post()
                .uri("/face-id/adopt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "jobId", jobId,
                        "jobToken", jobToken,
                        "userId", String.valueOf(userId)))
                .retrieve()
                .body(Map.class);
    }
}