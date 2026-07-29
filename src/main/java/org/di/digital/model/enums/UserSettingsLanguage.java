package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsLanguage {
    EN("Английский", "Ағылшын", "English"),
    RU("Русский", "Орыс", "Russian"),
    KZ("Казахский", "Қазақ", "Kazakh");

    private final String language;
    private final String kzName;
    private final String enName;

    UserSettingsLanguage(String language, String kzName, String enName) {
        this.language = language;
        this.kzName = kzName;
        this.enName = enName;
    }

    public String localized(UserSettingsLanguage target) {
        return switch (target) {
            case KZ -> kzName;
            case EN -> enName;
            case RU -> language;
        };
    }

    public static UserSettingsLanguage fromDisplayName(String displayName) {
        if (displayName == null) return null;
        for (UserSettingsLanguage lang : values()) {
            if (lang.getLanguage().equalsIgnoreCase(displayName.trim())) return lang;
        }
        try {
            return valueOf(displayName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid language: " + displayName +
                    ". Valid values: Английский, Русский, Казахский (or EN, RU, KZ)");
        }
    }

    public static UserSettingsLanguage resolve(String raw) {
        if (raw == null || raw.isBlank()) return RU;
        try {
            return fromDisplayName(raw);
        } catch (IllegalArgumentException e) {
            return RU;
        }
    }
}