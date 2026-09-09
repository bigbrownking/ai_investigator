package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.user.UpdateProfileRequest;
import org.di.digital.dto.request.user.UserSettingsRequest;
import org.di.digital.dto.response.access.MyAccessDto;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.AccessDeniedMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.settings.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.enums.settings.UserSettingsTheme;
import org.di.digital.model.user.*;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.*;
import org.di.digital.service.LogService;
import org.di.digital.service.UserService;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.util.mapper.UserMapper;
import org.di.digital.util.requests.UserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.*;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final AdministrationRepository administrationRepository;
    private final ProfessionRepository professionRepository;
    private final RankRepository rankRepository;
    private final LogService logService;
    private final CaseAccessService caseAccessService;
    private final UserUtil userUtil;
    private final UserMapper mapper;

    @Override
    public UserProfile getUserProfile(String email) {
        User user = userRepository.findByEmailWithSettings(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

        return mapper.toProfile(user);
    }

    @Override
    @Transactional
    public UserProfile updateUserSettings(String email, UserSettingsRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

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

        return mapper.toProfile(user);
    }

    @Override
    @Transactional
    public UserProfile updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

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
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.PROFESSION.localized(currentLang(), request.getProfessionId().toString())));
            changes.append(String.format("profession: [%s -> %s], ",
                    user.getProfession() != null ? user.getProfession().getRuName() : "null",
                    profession.getRuName()));
            user.setProfession(profession);
        }

        if (request.getRankId() != null) {
            Rank rank = rankRepository.findById(request.getRankId())
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.RANK.localized(currentLang(), request.getRankId().toString())));
            changes.append(String.format("rank: [%s -> %s], ",
                    user.getRank() != null ? user.getRank().getRuName() : "null",
                    rank.getRuName()));
            user.setRank(rank);
        }

        if (request.getAdministrationId() != null) {
            Administration administration = administrationRepository.findById(request.getAdministrationId())
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.ADMINISTRATION.localized(currentLang(), request.getAdministrationId().toString())));
            changes.append(String.format("administration: [%s -> %s], ",
                    user.getAdministration() != null ? user.getAdministration().getRuName() : "null",
                    administration.getRuName()));
            user.setAdministration(administration);
        }

        if (request.getRegionId() != null) {
            Region region = regionRepository.findById(request.getRegionId())
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.REGION.localized(currentLang(), request.getRegionId().toString())));
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

        return mapper.toProfile(user);
    }
    @Override
    public List<User> getMyBoss(String email) throws AccessDeniedException {
        User user = userRepository.findByEmailWithSettings(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

        if (user.getRegion() == null) {
            throw new AccessDeniedException(AccessDeniedMessage.USER_OUT_OF_REGION.localized(currentLang()));
        }

        return user.getRegion().getAdmins();
    }

    @Override
    public MyAccessDto getPermissions(Long userId, Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));
        userUtil.validateUserAccess(caseEntity, user);

        return caseAccessService.getMyPermissions(caseEntity, user);
    }

    private UserSettingsLanguage currentLang() {
        return getCurrentLang();
    }
}