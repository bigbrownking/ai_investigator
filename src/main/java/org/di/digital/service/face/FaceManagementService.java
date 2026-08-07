package org.di.digital.service.face;

import java.util.Map;

public interface FaceManagementService {
    Map<String, Object> resetOwnFace(String callerEmail);
    Map<String, Object> resetUserFace(String callerEmail, Long targetUserId);
}