package org.di.digital.model;

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
