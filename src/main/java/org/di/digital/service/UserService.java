package org.di.digital.service;

import org.di.digital.dto.request.UserSettingsRequest;
import org.di.digital.dto.response.UserProfile;

public interface UserService {
    UserProfile getUserProfile(String email);
    UserProfile updateUserSettings(String email, UserSettingsRequest request);
}
