package org.di.digital.service.face;

import org.di.digital.dto.response.auth.JwtResponse;

public interface FaceCompleteService {
    JwtResponse complete(String authHeader, String jobId);
}