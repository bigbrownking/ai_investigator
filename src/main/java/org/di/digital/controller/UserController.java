package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.UserSettingsRequest;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.UserProfile;
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

    @PatchMapping("/profession")
    public ResponseEntity<UserProfile> updateProfession(
            Authentication authentication,
            @RequestParam String profession
    ) {
        String email = authentication.getName();
        log.info("Updating profession for {} to {}", email, profession);
        UserProfile updatedProfile = userService.updateProfession(email, profession);
        return ResponseEntity.ok(updatedProfile);
    }

    @PatchMapping("/region")
    public ResponseEntity<UserProfile> updateRegion(
            Authentication authentication,
            @RequestParam String region
    ) {
        String email = authentication.getName();
        log.info("Updating region for {} to {}", email, region);
        UserProfile updatedProfile = userService.updateRegion(email, region);
        return ResponseEntity.ok(updatedProfile);
    }

    @PatchMapping("/street")
    public ResponseEntity<UserProfile> updateStreet(
            Authentication authentication,
            @RequestParam String region
    ) {
        String email = authentication.getName();
        log.info("Updating region for {} to {}", email, region);
        UserProfile updatedProfile = userService.updateRegion(email, region);
        return ResponseEntity.ok(updatedProfile);
    }
}

