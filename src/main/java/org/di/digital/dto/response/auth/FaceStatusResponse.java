package org.di.digital.dto.response.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaceStatusResponse {
    private boolean enabled;
    private int templatesCount;
}
