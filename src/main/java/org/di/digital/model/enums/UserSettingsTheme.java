package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsTheme {
    DARK("Темная", "Қараңғы", "Dark"),
    LIGHT("Светлая", "Ашық", "Light");

    private final String theme;
    private final String kzName;
    private final String enName;

    UserSettingsTheme(String theme, String kzName, String enName) {
        this.theme = theme;
        this.kzName = kzName;
        this.enName = enName;
    }

    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kzName;
            case EN -> enName;
            case RU -> theme;
        };
    }

    public static UserSettingsTheme fromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        for (UserSettingsTheme theme : values()) {
            if (theme.getTheme().equalsIgnoreCase(displayName.trim())) {
                return theme;
            }
        }

        try {
            return valueOf(displayName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid theme: " + displayName +
                    ". Valid values: Темная, Светлая (or DARK, LIGHT)");
        }
    }
}