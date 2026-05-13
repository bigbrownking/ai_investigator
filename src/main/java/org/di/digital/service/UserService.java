package org.di.digital.service;

import org.di.digital.dto.request.UpdateProfileRequest;
import org.di.digital.dto.request.UserSettingsRequest;
import org.di.digital.dto.response.UserProfile;
import org.springframework.data.mongodb.repository.query.MongoQueryExecution;

public interface UserService {
    UserProfile getUserProfile(String email);
    UserProfile updateUserSettings(String email, UserSettingsRequest request);
    UserProfile updateUserProfile(String email, UpdateProfileRequest request);
}
