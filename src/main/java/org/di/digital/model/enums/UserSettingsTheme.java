package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsTheme {
    DARK("dark"),
    LIGHT("light");

    private final String theme;

    UserSettingsTheme(String theme) {
        this.theme = theme;
    }
}
