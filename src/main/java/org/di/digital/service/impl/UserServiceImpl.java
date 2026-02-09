package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.UserSettingsRequest;
import org.di.digital.dto.response.UserProfile;
import org.di.digital.model.User;
import org.di.digital.model.UserSettings;
import org.di.digital.model.enums.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.di.digital.model.enums.UserSettingsTheme;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.UserService;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public UserProfile updateUserSettings(String email, UserSettingsRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        UserSettings settings = user.getSettings();
        if (settings == null) {
            settings = new UserSettings();
            settings.setUser(user);
        }

        if (request.getLevel() != null) {
            settings.setLevel(UserSettingsDetalizationLevel.fromDisplayName(request.getLevel()));
        }

        if (request.getLanguage() != null) {
            settings.setLanguage(UserSettingsLanguage.fromDisplayName(request.getLanguage()));
        }

        if (request.getTheme() != null) {
            settings.setTheme(UserSettingsTheme.fromDisplayName(request.getTheme()));
        }

        user.setSettings(settings);
        userRepository.save(user);

        log.info("Updated settings for user {}: level={}, language={}, theme={}",
                email, settings.getLevel(), settings.getLanguage(), settings.getTheme());

        return mapper.mapToUserProfileResponse(user);
    }
}