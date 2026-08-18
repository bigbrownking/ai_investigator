package org.di.digital.model.enums.dictionary;

import lombok.Getter;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
public enum InterrogationDocType {
    IIN("ИИН", "ЖСН", "IIN"),
    PASSPORT("Паспорт", "Паспорт", "Passport");
    private final String ru;
    private final String kz;
    private final String en;

    InterrogationDocType(String ru, String kz, String en) {
        this.ru = ru;
        this.kz = kz;
        this.en = en;
    }

    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kz;
            case EN -> en;
            case RU -> ru;
        };
    }
}
