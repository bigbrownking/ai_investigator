package org.di.digital.dto.face;

import java.util.Map;

public record FaceJobEvent(
        Long userId,
        String jobToken,
        Map<String, Object> job    // the JobView as a generic map
) {}