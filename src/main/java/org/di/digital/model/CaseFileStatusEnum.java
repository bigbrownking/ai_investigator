package org.di.digital.model;

import lombok.Getter;

@Getter
public enum CaseFileStatusEnum {
    UPLOADED("Загружен"), // minio
    QUEUED("В очереди"), //mongo
    PENDING("В ожидании"), //rabbitmq
    PROCESSING("В обработке"), // model
    COMPLETED("Обработан"),
    FAILED("Ошибка");

    private final String label;
    CaseFileStatusEnum(String s) {
        this.label = s;
    }
}
