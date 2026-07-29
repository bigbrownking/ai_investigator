package org.di.digital.model.enums.dictionary;

import lombok.Getter;
import org.di.digital.model.enums.UserSettingsLanguage;

@Getter
public enum InterrogationRole {
    WITNESS("Свидетель", "Куә", "Witness"),
    SUSPECT("Подозреваемый", "Күдікті", "Suspect"),
    VICTIM("Потерпевший", "Жәбірленуші", "Victim"),
    WITNESS_WITH_DEFENSE("Свидетель, имеющий право на защиту", "Қорғану құқығы бар куә", "Witness with right to defense"),
    SPECIALIST("Специалист", "Маман", "Specialist"),
    EXPERT("Эксперт", "Сарапшы", "Expert");
    private final String ru;
    private final String kz;
    private final String en;

    InterrogationRole(String ru, String kz, String en) {
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
