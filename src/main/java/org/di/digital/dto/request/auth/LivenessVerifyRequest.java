package org.di.digital.dto.request.auth;

import lombok.Data;
import org.di.digital.model.enums.LivenessStep;

import java.util.List;

@Data
public class LivenessVerifyRequest {
    private String livenessId;
    private String iin;
    private List<LivenessFrame> frames;

    @Data
    public static class LivenessFrame {
        private LivenessStep step;
        private String image;
    }
}
