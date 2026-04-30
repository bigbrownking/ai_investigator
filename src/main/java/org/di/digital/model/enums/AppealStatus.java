package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum AppealStatus {
    PENDING("На рассмотрении"),
    APPROVED("Одобрено"),
    REJECTED("Отклонено");
    private final String description;

    AppealStatus(String description) {
        this.description = description;
    }
}
