package org.di.digital.dto.request.auth;

import lombok.Data;

import java.util.List;

@Data
public class FaceLoginRequest {
    private String iin;
    private List<Double> descriptor;
    private String challengeId;
    private String livenessToken;
}
