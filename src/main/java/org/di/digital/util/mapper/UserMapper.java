package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.user.*;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.user.*;
import org.di.digital.repository.user.RegionRepository;
import org.di.digital.util.LocalizationHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final LocalizationHelper localizationHelper;
    private final RegionRepository regionRepository;

    @Value("${last.seen.ttl}")
    private int ttl;

    public UserDto toDto(User user) {
        var lang = user.getSettings().getLanguage();
        return UserDto.builder()
                .id(user.getId())
                .iin(user.getIin())
                .name(user.getName())
                .surname(user.getSurname())
                .fathername(user.getFathername())
                .email(user.getEmail())
                .administration(localizationHelper.getLocalizedName(user.getAdministration(), lang))
                .profession(localizationHelper.getLocalizedName(user.getProfession(), lang))
                .rank(localizationHelper.getLocalizedName(user.getRank(), lang))
                .region(localizationHelper.getLocalizedName(user.getRegion(), lang))
                .build();
    }

    public UserProfile toProfile(User user) {
        var lang = user.getSettings().getLanguage();
        Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        return UserProfile.builder()
                .id(user.getId())
                .iin(user.getIin())
                .name(user.getName())
                .surname(user.getSurname())
                .fathername(user.getFathername())
                .role(roles)
                .administration(localizationHelper.getLocalizedName(user.getAdministration(), lang))
                .profession(localizationHelper.getLocalizedName(user.getProfession(), lang))
                .rank(localizationHelper.getLocalizedName(user.getRank(), lang))
                .region(localizationHelper.getLocalizedName(user.getRegion(), lang))
                .responsibleRegions(responsibleRegions(user, lang))
                .email(user.getEmail())
                .faceEnabled(user.isFaceEnabled())
                .active(user.isActive())
                .online(user.isOnline(ttl))
                .settings(toSettingsDto(user, lang))
                .street(localizationHelper.getLocalizedName(primaryAddress(user), lang))
                .createdCaseCount(user.getOwnedCases() != null ? user.getOwnedCases().size() : 0)
                .lastSeenAt(LastSeenFormatter.format(user.getLastSeenAt()))
                .build();
    }

    private UserSettingsDto toSettingsDto(User user, UserSettingsLanguage lang) {
        if (user.getSettings() == null) return null;
        return UserSettingsDto.builder()
                .level(user.getSettings().getLevel() != null ? user.getSettings().getLevel().name() : null)
                .language(lang.getLanguage())
                .theme(user.getSettings().getTheme().getTheme())
                .build();
    }

    private Address primaryAddress(User user) {
        if (user.getRegion() != null && !user.getRegion().getAddresses().isEmpty()) {
            return user.getRegion().getAddresses().get(0);
        }
        return null;
    }

    private List<String> responsibleRegions(User user, UserSettingsLanguage lang) {
        return regionRepository.findByAdminsContaining(user).stream()
                .map(region -> localizationHelper.getLocalizedName(region, lang))
                .toList();
    }
}