package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum LogAction {
    LOGIN("Вход пользователя"),
    SIGNUP("Регирстрация пользователя"),
    CASE_CREATED("Создание дела"),
    CASE_STATUS_CHANGED("Изменение статуса дела"),
    CASE_DELETED("Удаление дела"),
    FILE_UPLOAD("Загрузка документа"),
    FILE_DELETE("Удаление документа"),
    USER_ADD("Добавление пользователя"),
    USER_DELETE("Удаление пользователя"),
    CHAT_MESSAGE("Сообщение в чате"),
    CHAT_CLEAR("Очистка чата"),
    FILE_DOWNLOAD("Скачивание документа"),
    QUALIFICATION("Генерация квалификации"),
    INDICTMENT("Генерация обвнительного акта"),
    INDICTMENT_FINAL("Генерация заключения"),
    PLAN_GENERATION("Генерация плана"),
    QUALIFICATION_DOWNLOAD("Скачивание квалификации"),
    INDICTMENT_DOWNLOAD("Скачивание обвинительного акта"),
    INTERROGATION_DOWNLOAD("Скачивание допроса"),
    INTERROGATION_ADDED("Создание допроса"),
    INTERROGATION_DELETED("Удаление допроса"),
    FIGURANT_ADDED("Добавление фигуранта"),
    FIGURANT_DELETED("Удаление фигуранта"),
    MESSAGE_SELECTED("Выбор сообщения в чате"),
    CASE_UPDATED("Обновление дела"),
    AUDIO_UPLOADED("Загрузка айдио"),
    INTERROGATION_COMPLETED("Завершение допроса"),
    NO_FILE_PROCESSED("Ни одного обработанного файла"),
    NO_INTERROGATION_CLOSED("Не все допросы завершены"),
    NO_QUALIFICATION("Нет квалификации"),
    NO_SUCH_FILE("Нет необходимых файлов"),
    NO_ACCESS("Нет доступа");

    private final String description;

    LogAction(String description) {
        this.description = description;
    }
}
