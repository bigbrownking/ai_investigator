package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum PlanStatus {
    DRAFT("Черновик"),
    PENDING("На рассмотрении"),
    APPROVED_L1("Одобрено заместителем руководителя управления"),
    APPROVED_L2("Одобрено руководителем управления"),
    APPROVED_L3("Одобрено заместителем руководителя департамента"),
    REJECTED("Отклонено"),
    WITHDRAWN("Отозвано");
    private final String description;
    PlanStatus(String description) {
        this.description = description;
    }

}