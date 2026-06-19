package org.di.digital.dto.request.auth;

import lombok.Data;

import java.util.List;

@Data
public class FaceEnrollRequest {
    private List<Double> descriptor;
    private String livenessToken;
}
