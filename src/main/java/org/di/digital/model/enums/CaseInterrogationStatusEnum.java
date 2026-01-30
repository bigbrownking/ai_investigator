package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum CaseInterrogationStatusEnum {
    PAUSED("Приостановлено"),
    COMPLETED("Завершено"),
    IN_PROGRESS("В процессе");
    private final String label;
    CaseInterrogationStatusEnum(String s) {
        this.label = s;
    }
}
