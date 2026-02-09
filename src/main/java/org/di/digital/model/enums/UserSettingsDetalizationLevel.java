package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsDetalizationLevel {
    LOW("Легкий"),
    MEDIUM("Средний"),
    HIGH("Тяжелый");

    private final String level;

    UserSettingsDetalizationLevel(String level) {
        this.level = level;
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
