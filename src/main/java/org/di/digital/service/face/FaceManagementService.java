package org.di.digital.service.face;

import org.di.digital.dto.response.face.FaceResetResponse;

public interface FaceManagementService {
    FaceResetResponse resetOwnFace(String email);
    FaceResetResponse resetUserFace(String email, Long targetUserId);
}