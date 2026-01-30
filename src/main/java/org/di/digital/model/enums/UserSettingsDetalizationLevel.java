package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum UserSettingsDetalizationLevel {
    LOW("NAIVE"),
    MEDIUM("LOCAL"),
    HIGH("HYBRID");

    private final String level;

    UserSettingsDetalizationLevel(String level) {
        this.level = level;
    }
}
