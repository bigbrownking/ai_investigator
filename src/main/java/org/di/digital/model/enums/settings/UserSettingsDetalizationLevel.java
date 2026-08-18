package org.di.digital.model.enums.settings;

import lombok.Getter;

@Getter
public enum UserSettingsDetalizationLevel {
    LOW("Легкий", "Жеңіл", "Easy"),
    MEDIUM("Средний", "Орташа", "Medium"),
    HIGH("Тяжелый", "Ауыр", "Hard");

    private final String level;
    private final String kzName;
    private final String enName;

    UserSettingsDetalizationLevel(String level, String kzName, String enName) {
        this.level = level;
        this.kzName = kzName;
        this.enName = enName;
    }

    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kzName;
            case EN -> enName;
            case RU -> level;
        };
    }

    public static UserSettingsDetalizationLevel fromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        for (UserSettingsDetalizationLevel level : values()) {
            if (level.getLevel().equalsIgnoreCase(displayName.trim())) {
                return level;
            }
        }

        try {
            return valueOf(displayName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid detalization level: " + displayName +
                    ". Valid values: Легкий, Средний, Тяжелый (or LOW, MEDIUM, HIGH)");
        }
    }
}