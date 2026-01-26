package org.di.digital.dto.request;

import lombok.Getter;

@Getter
public enum FileType {
    QUALIFICATION("qualification"),
    REGULAR("regular");

    private final String value;

    FileType(String value) {
        this.value = value;
    }

    public static FileType fromString(String text) {
        for (FileType type : FileType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid file type: " + text);
    }
}