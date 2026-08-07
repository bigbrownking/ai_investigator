package org.di.digital.dto.response.face;

import lombok.Builder;

import java.util.Map;

@Builder
public record FaceResetResponse(
        boolean deleted,
        Long userId,
        boolean faceEnabled,
        Map<String, Object> faceAuth
) {}