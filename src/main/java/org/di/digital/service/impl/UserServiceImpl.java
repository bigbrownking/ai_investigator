package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.UpdateProfileRequest;
import org.di.digital.dto.request.UserSettingsRequest;
import org.di.digital.dto.response.UserProfile;
import org.di.digital.model.*;
import org.di.digital.model.enums.*;
import org.di.digital.repository.*;
import org.di.digital.service.LogService;
import org.di.digital.service.UserService;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final AdministrationRepository administrationRepository;
    private final ProfessionRepository professionRepository;
    private final RankRepository rankRepository;
    private final LogService logService;
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

    @Override
    @Transactional
    public UserProfile updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        StringBuilder changes = new StringBuilder();

        if (request.getName() != null) {
            changes.append(String.format("name: [%s -> %s], ", user.getName(), request.getName()));
            user.setName(request.getName());
        }

        if (request.getSurname() != null) {
            changes.append(String.format("surname: [%s -> %s], ", user.getSurname(), request.getSurname()));
            user.setSurname(request.getSurname());
        }

        if (request.getFathername() != null) {
            changes.append(String.format("fathername: [%s -> %s], ", user.getFathername(), request.getFathername()));
            user.setFathername(request.getFathername());
        }

        if (request.getProfessionId() != null) {
            Profession profession = professionRepository.findById(request.getProfessionId())
                    .orElseThrow(() -> new IllegalStateException("Profession not found"));
            changes.append(String.format("profession: [%s -> %s], ",
                    user.getProfession() != null ? user.getProfession().getRuName() : "null",
                    profession.getRuName()));
            user.setProfession(profession);
        }

        if (request.getRankId() != null) {
            Rank rank = rankRepository.findById(request.getRankId())
                    .orElseThrow(() -> new IllegalStateException("Rank not found"));
            changes.append(String.format("rank: [%s -> %s], ",
                    user.getRank() != null ? user.getRank().getName() : "null",
                    rank.getName()));
            user.setRank(rank);
        }

        if (request.getAdministrationId() != null) {
            Administration administration = administrationRepository.findById(request.getAdministrationId())
                    .orElseThrow(() -> new IllegalStateException("Administration not found"));
            changes.append(String.format("administration: [%s -> %s], ",
                    user.getAdministration() != null ? user.getAdministration().getRuName() : "null",
                    administration.getRuName()));
            user.setAdministration(administration);
        }

        if (request.getRegionId() != null) {
            Region region = regionRepository.findById(request.getRegionId())
                    .orElseThrow(() -> new IllegalStateException("Region not found"));
            changes.append(String.format("region: [%s -> %s], ",
                    user.getRegion() != null ? user.getRegion().getRuName() : "null",
                    region.getRuName()));
            user.setRegion(region);
        }

        userRepository.save(user);

        String changesLog = !changes.isEmpty()
                ? changes.substring(0, changes.length() - 2)
                : "no changes";

        log.info("Profile updated for user [{}]: {}", email, changesLog);

        logService.log(
                String.format("User [%s] updated profile. Changes: %s", email, changesLog),
                LogLevel.INFO,
                LogAction.USER_UPDATED,
                null,
                email
        );

        return mapper.mapToUserProfileResponse(user);
    }
}