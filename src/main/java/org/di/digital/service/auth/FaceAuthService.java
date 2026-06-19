package org.di.digital.service.auth;

import org.di.digital.dto.request.auth.FaceLoginRequest;
import org.di.digital.dto.request.auth.LivenessVerifyRequest;
import org.di.digital.dto.response.JwtResponse;
import org.di.digital.dto.response.auth.ChallengeResponse;
import org.di.digital.dto.response.auth.FaceStatusResponse;
import org.di.digital.dto.response.auth.LivenessChallengeResponse;
import org.di.digital.dto.response.auth.LivenessVerifyResponse;

import java.util.List;
import java.util.Map;

public interface FaceAuthService {
    ChallengeResponse generateChallenge(String iin);
    LivenessChallengeResponse generateLivenessChallenge(String iin);
    LivenessVerifyResponse verifyLiveness(LivenessVerifyRequest request);
    void enroll(String email, List<Double> descriptor, String livenessToken);
    JwtResponse faceLogin(FaceLoginRequest request);
    void removeFace(String email);
    FaceStatusResponse getFaceStatus(String email);
}
