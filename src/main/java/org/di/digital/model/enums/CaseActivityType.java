package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum CaseActivityType {
    CASE_CREATED("Дело создано"),
    FILE_UPLOADED("Документ загружен"),
    FILE_DELETED("Документ удален"),
    CHAT_MESSAGE("Отправка сообщения в чат"),
    QUALIFICATION_GENERATED("Генерация квалификации"),
    INDICTMENT_GENERATED("Генерация обвинительного акта"),
    INTERROGATION_ADDED("Допрос добавлен"),
    USER_ADDED("Пользователь добавлен в дело"),
    USER_REMOVED("Пользователь удален из дела"),
    STATUS_CHANGED("Статус дела изменен"),;

    private final String description;

    CaseActivityType(String description) {
        this.description = description;
    }

}