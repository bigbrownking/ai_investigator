package org.di.digital.exception.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
@RequiredArgsConstructor
public enum IllegalStateMessage implements LocalizedMessage {
    ALREADY_EXISTS("Объект уже существует", "Объект бұрыннан бар"),
    INVALID_STATE("Недопустимое состояние", "Қолайсыз күй"),
    INVALID_OPERATION("Недопустимая операция", "Қолайсыз операция"),
    INVALID_INPUT("Недопустимый ввод", "Қолайсыз енгізу"),
    INVALID_OUTPUT("Недопустимый вывод", "Қолайсыз шығару");
    private final String ru;
    private final String kz;

    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kz;
            default -> ru;
        };
    }

    public String localized(UserSettingsLanguage lang, String detail) {
        return localized(lang) + ": " + detail;
    }
}
