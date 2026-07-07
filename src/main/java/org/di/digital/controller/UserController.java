package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.user.UpdateProfileRequest;
import org.di.digital.dto.request.user.UserSettingsRequest;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfile> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        log.info("Fetching profile for {}", email);
        return ResponseEntity.ok(userService.getUserProfile(email));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserProfile> updateMySettings(
            Authentication authentication,
            @RequestBody UserSettingsRequest settingsRequest
    ) {
        String email = authentication.getName();
        log.info("Updating settings for {}", email);
        UserProfile updatedProfile = userService.updateUserSettings(email, settingsRequest);
        return ResponseEntity.ok(updatedProfile);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfile> updateMyProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest settingsRequest
    ) {
        String email = authentication.getName();
        log.info("Updating profile for {}", email);
        UserProfile updatedProfile = userService.updateUserProfile(email, settingsRequest);
        return ResponseEntity.ok(updatedProfile);
    }
}

