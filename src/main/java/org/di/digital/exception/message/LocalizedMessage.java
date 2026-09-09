package org.di.digital.exception.message;

import org.di.digital.model.enums.settings.UserSettingsLanguage;

public interface LocalizedMessage {
    String getRu();
    String getKz();

    default String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> getKz();
            default -> getRu();
        };
    }

    default String localized(UserSettingsLanguage lang, String detail) {
        return localized(lang) + ": " + detail;
    }
}