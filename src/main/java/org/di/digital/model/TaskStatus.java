package org.di.digital.model;

import lombok.Getter;

@Getter
public enum TaskStatus {
    PENDING("Ожидает отправки"),      // В MongoDB, не отправлена в RabbitMQ
    PROCESSING("В RabbitMQ"),         // Отправлена в RabbitMQ, обрабатывается
    COMPLETED("Завершена"),           // Успешно обработана
    FAILED("Ошибка");                 // Ошибка обработки

    private final String label;

    TaskStatus(String s) {
        this.label = s;
    }
}
