package org.di.digital.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ActionStatus {
    EXECUTED("Исполнен"),
    NOT_EXECUTED("Не исполнен");

    private final String value;

    public static ActionStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(s -> s.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Недопустимый статус: " + value));
    }
}
