package org.di.digital.model.enums.permission;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
@RequiredArgsConstructor
public enum CaseMemberType {
    MEMBER("Участник", "Қатысушы"),
    SOG("Участник СОГ", "СОГ қатысушысы");

    private final String ru;
    private final String kz;

    public String localized(UserSettingsLanguage lang) {
        return lang == UserSettingsLanguage.RU ? ru : kz;
    }
}