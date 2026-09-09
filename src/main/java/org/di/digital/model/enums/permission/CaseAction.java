package org.di.digital.model.enums.permission;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
@RequiredArgsConstructor
public enum CaseAction {
    READ("Просмотр", "Қарау"),
    UPDATE("Редактирование", "Өңдеу"),
    ADD("Добавление", "Қосу"),
    DELETE("Удаление", "Жою"),
    DOWNLOAD("Скачивание", "Жүктеу");

    private final String ru;
    private final String kz;

    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kz;
            default -> ru;
        };
    }
}
