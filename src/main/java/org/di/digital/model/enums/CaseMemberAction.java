package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum CaseMemberAction {
    ADD("Добавлен"),
    REMOVE("Удалён");

    private final String description;

    CaseMemberAction(String description) {
        this.description = description;
    }
}