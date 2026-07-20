package org.di.digital.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LivenessStep {
    LOOK_CENTER("Посмотрите прямо"),
    TURN_LEFT("Поверните голову налево"),
    TURN_RIGHT("Поверните голову направо");
    private final String instruction;
}
