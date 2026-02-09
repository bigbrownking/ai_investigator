package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsTheme {
    DARK("Темная"),
    LIGHT("Светлая");

    private final String theme;

    UserSettingsTheme(String theme) {
        this.theme = theme;
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
