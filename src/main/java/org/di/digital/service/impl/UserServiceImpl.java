package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.UserSettingsRequest;
import org.di.digital.dto.response.UserProfile;
import org.di.digital.dto.response.UserSettingsDto;
import org.di.digital.model.Role;
import org.di.digital.model.User;
import org.di.digital.model.UserSettings;
import org.di.digital.model.enums.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.di.digital.model.enums.UserSettingsTheme;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.UserService;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final Mapper mapper;

    @Override
    public UserProfile getUserProfile(String email) {
        User user = userRepository.findByEmailWithSettings(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        return mapper.mapToUserProfileResponse(user);
    }

    @Override
    public UserProfile updateUserSettings(String email, UserSettingsRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        UserSettings settings = user.getSettings();
        if (settings == null) {
            settings = new UserSettings();
            settings.setUser(user);
        }

        settings.setLevel(request.getLevel() != null ? UserSettingsDetalizationLevel.valueOf(request.getLevel()) : settings.getLevel());
        settings.setLanguage(request.getLanguage() != null ? UserSettingsLanguage.valueOf(request.getLanguage()) : settings.getLanguage());
        settings.setTheme(request.getTheme() != null ? UserSettingsTheme.valueOf(request.getTheme()) : settings.getTheme());

        user.setSettings(settings);
        userRepository.save(user);

        return mapper.mapToUserProfileResponse(user);
    }

}

