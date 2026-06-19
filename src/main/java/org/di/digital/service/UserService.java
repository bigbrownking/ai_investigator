package org.di.digital.service;

import org.di.digital.dto.request.UpdateProfileRequest;
import org.di.digital.dto.request.UserSettingsRequest;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.model.user.User;

import java.util.List;

public interface UserService {
    UserProfile getUserProfile(String email);
    UserProfile updateUserSettings(String email, UserSettingsRequest request);
    UserProfile updateUserProfile(String email, UpdateProfileRequest request);
    List<User> getMyBoss(String email);
}
