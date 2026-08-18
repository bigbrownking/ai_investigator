package org.di.digital.model.enums.dictionary;

import lombok.Getter;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
public enum ModuleType {
    DOCUMENTS("Документы", "Құжаттар", "Documents"),
    PARTICIPANTS("Участники", "Қатысушылар", "Participants"),
    AI_CHAT("Чат ИИ", "ЖИ чат", "AI Chat"),
    PLAN("План", "Жоспар", "Plan"),
    INSPECTION("Осмотр", "Қарау", "Osmotr"),
    INTERROGATIONS("Допросы", "Жауап алу", "Interrogations"),
    QUALIFICATION("Квалификация", "Саралау", "Qualification"),
    INDICTMENT("Обвинительный акт", "Айыптау актісі", "Indictment"),
    OTHER("Прочее", "Басқа", "Other");

    private final String ru;
    private final String kz;
    private final String en;

    ModuleType(String ru, String kz, String en) {
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
    public static ModuleType from(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();

        for (ModuleType m : values()) {
            if (m.name().equalsIgnoreCase(v)) return m;
        }
        for (ModuleType m : values()) {
            if (m.ru.equalsIgnoreCase(v) || m.kz.equalsIgnoreCase(v) || m.en.equalsIgnoreCase(v)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Неизвестный модуль: " + raw);
    }
}