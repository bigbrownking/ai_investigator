package org.di.digital.model.enums.permission;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
@RequiredArgsConstructor
public enum CaseModule {
    CASE("Дело", "Іс"),
    DOCUMENTS("Документы", "Құжаттар"),
    CHAT("Чат", "Чат"),
    INTERROGATION("Допрос", "Жауап алу"),
    OSMOTR("Осмотр", "Қарау"),
    REPORT("Справка", "Есеп"),
    USERS("Пользователи", "Пайдаланушылар"),
    QUALIFICATION("Квалификация", "Саралау"),
    INDICTMENT("Обвинительный акт", "Айыптау актісі"),
    PLAN("План", "Жоспар");

    private final String ru;
    private final String kz;

    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kz;
            default -> ru;
        };
    }
}