package org.di.digital.exception.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
@RequiredArgsConstructor
public enum NotFoundMessage implements LocalizedMessage {

    USER("Пользователь не найден", "Пайдаланушы табылмады"),
    CASE("Дело не найдено", "Іс табылмады"),
    MODULE("Модуль не найден", "Модуль табылмады"),
    REPORT("Справка не найдена", "Анықтама табылмады"),
    NOTIFICATION("Уведомление не найдено", "Хабарлама табылмады"),
    PROFESSION("Профессия не найдена", "Кәсіп табылмады"),
    RANK("Звание не найдено", "Дәрежесі табылмады"),
    MESSAGE("Сообщение не найдено", "Хабарлама табылмады"),
    CHAT("Чат не найден", "Чат табылмады"),
    ACTION("Действие не найдено", "Әрекет табылмады"),
    OSMOTR("Осмотр не найден", "Тексеру табылмады"),
    SEGMENT("Сегмент не найден", "Сегмент табылмады"),
    EDUCATION("Образование не найдено", "Білім табылмады"),
    MILITARY("Воинская учет не найдена", "Әскери учет табылмады"),
    CRIMINAL("Судимость не найдена", "Қылмыстық іс табылмады"),
    RELATION("Связь не найдена", "Қатынас табылмады"),
    INVOLVED("Вовлеченные люди не найдены", "Қатысушы табылмады"),
    QA("Вопрос-ответ не найден", "Сұрақ-жауап табылмады"),
    AUDIO("Аудиозапись не найдена", "Аудиожазба табылмады"),
    INDICTMENT("Обвинительный акт не найден", "Айыптау актісі табылмады"),
    SECTION("Раздел не найден", "Бөлім табылмады"),
    ADMINISTRATION("Управление не найдена", "Бөлімше табылмады"),
    ROLE("Роль не найдена", "Рөл табылмады"),
    REGION("Регион не найден", "Аймақ табылмады"),
    FL("Человека с этим документов не найдено", "Бұл құжаты бар ешкім табылмады"),
    PLAN("План не найден", "Жоспар табылмады"),
    INTERROGATION("Допрос не найден", "Жауап алу табылмады"),
    APPEAL("Обращение не найдено", "Өтінім табылмады"),
    SUPPORT_TICKET("Обращение в поддержку не найдено", "Қолдау сұрауы табылмады"),
    REVIEW("Отзыв не найден", "Пікір табылмады"),
    FILE("Файл не найден", "Файл табылмады");

    private final String ru;
    private final String kz;
    public String localized(UserSettingsLanguage lang, String detail) {
        return localized(lang) + ": " + detail;
    }
    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kz;
            default -> ru;
        };
    }
}
