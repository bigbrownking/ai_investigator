package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum MessageConstant {
    CANNOT_DELETE_FILE("Нельзя удалить документ в обработке в деле: %s"),
    CANNOT_CREATE_INTERROGATION("Вы не можете создать новый допрос в деле: %s"),
    CANNOT_UPLOAD_FILE("Вы не можете загружать новые документы в деле: %s"),
    NO_FILE_PROCESSED("Должно быть обработано не менее одного документа в деле: %s"),
    NO_QUALIFICATION("Не найдено документа помеченного как квалификация дела в деле: %s"),
    ALL_FILES_PROCESSED("Все документы должны быть обработаны в деле: %s"),
    ALL_INTERROGATION_PROCESSED("Все допросы должны быть завершены в деле: %s");
    private final String template;

    MessageConstant(String template) {
        this.template = template;
    }

    public String format(Object... args) {
        return String.format(template, args);
    }
}
