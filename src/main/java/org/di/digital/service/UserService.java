package org.di.digital.service;

import org.di.digital.dto.request.user.UpdateProfileRequest;
import org.di.digital.dto.request.user.UserSettingsRequest;
import org.di.digital.dto.response.access.MyAccessDto;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.user.User;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserService {
    UserProfile getUserProfile(String email);
    UserProfile updateUserSettings(String email, UserSettingsRequest request);
    UserProfile updateUserProfile(String email, UpdateProfileRequest request);
    List<User> getMyBoss(String email) throws AccessDeniedException;
    MyAccessDto getPermissions(Long userId, Long caseId);
}
