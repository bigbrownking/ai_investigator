package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsLanguage {
    EN("Английский"),
    RU("Русский"),
    KZ("Казахский");
    private final String language;

    UserSettingsLanguage(String language) {
        this.language = language;
    }

    public static UserSettingsLanguage fromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        for (UserSettingsLanguage lang : values()) {
            if (lang.getLanguage().equalsIgnoreCase(displayName.trim())) {
                return lang;
            }
        }

        // Fallback: try to match by enum name
        try {
            return valueOf(displayName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid language: " + displayName +
                    ". Valid values: Английский, Русский, Казахский (or EN, RU, KZ)");
        }
    }
}
