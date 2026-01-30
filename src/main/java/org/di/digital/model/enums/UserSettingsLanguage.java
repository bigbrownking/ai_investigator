package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsLanguage {
    EN("en"),
    RU("ru"),
    KZ("kz");
    private final String language;

    UserSettingsLanguage(String language) {
        this.language = language;
    }
}
