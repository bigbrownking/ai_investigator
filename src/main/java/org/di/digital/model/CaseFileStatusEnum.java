package org.di.digital.model;

import lombok.Getter;

@Getter
public enum CaseFileStatusEnum {
    UPLOADED("Загружен"),
    QUEUED("В очереди"),
    PENDING("В ожидании"),
    PROCESSING("В обработке"),
    COMPLETED("Завершен"),
    FAILED("Ошибка");
    private final String label;
    CaseFileStatusEnum(String s) {
        this.label = s;
    }
}
