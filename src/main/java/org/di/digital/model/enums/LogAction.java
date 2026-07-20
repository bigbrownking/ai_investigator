package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum LogAction {
    LOGIN("Вход пользователя"),
    FACE_ENROLL("Регистрация лица"),
    FACE_LOGIN_FAILED("Неудачная попытка входа по лицу"),
    FACE_LOGIN("Успешный вход по лицу"),
    FACE_REMOVE("Удаление лица"),
    SIGNUP("Регирстрация пользователя"),
    CASE_CREATED("Создание дела"),
    CASE_STATUS_CHANGED("Изменение статуса дела"),
    CASE_DELETED("Удаление дела"),
    PLAN_GENERATED("Генерация плана"),
    PLAN_WITHDRAWN("Отзыв плана"),
    PLAN_SUBMITTED("Отправка плана на рассмотрение"),
    PLAN_APPROVED("Одобрение плана"),
    PLAN_REJECTED("Отклонение плана"),
    PLAN_POINT_OVERDUE("Просрочка по пункту плана"),
    FILE_UPLOAD("Загрузка документа"),
    FILE_REORDER("Изменение порядка документов"),
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
    PLAN_DOWNLOAD("Скачивание плана"),
    INDICTMENT_DOWNLOAD("Скачивание обвинительного акта"),
    INTERROGATION_DOWNLOAD("Скачивание допроса"),
    INTERROGATION_ADDED("Создание допроса"),
    INTERROGATION_DELETED("Удаление допроса"),
    FIGURANT_ADDED("Добавление фигуранта"),
    FIGURANT_DELETED("Удаление фигуранта"),
    QA_CREATED("Добавление вопроса в допросе"),
    MESSAGE_SELECTED("Выбор сообщения в чате"),
    REFORMULATE("Переформулировать вопрос в допросе"),
    CASE_UPDATED("Обновление дела"),
    CASE_LANGUAGE_UPDATE("Смена языка в деле"),
    AUDIO_UPLOADED("Загрузка айдио"),
    INTERROGATION_COMPLETED("Завершение допроса"),
    USER_UPDATED("Обновление данных пользователя"),
    NO_FILE_PROCESSED("Ни одного обработанного файла"),
    NO_INTERROGATION_CLOSED("Не все допросы завершены"),
    NO_QUALIFICATION("Нет квалификации"),
    NO_SUCH_FILE("Нет необходимых файлов"),
    INTERROGATION_CATEGORY_CONFIRMED("Подтверждение категории допрашиваемого"),
    INTERROGATION_BREAK_STARTED("Начало перерыва допроса"),
    INTERROGATION_LIMIT_OVERRIDE("Превышение лимита проведения допроса"),
    NO_ACCESS("Нет доступа");

    private final String description;

    LogAction(String description) {
        this.description = description;
    }
}
