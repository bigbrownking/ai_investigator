package org.di.digital.exception.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
@RequiredArgsConstructor
public enum AccessDeniedMessage implements LocalizedMessage{
    PLAN_APPROVE("У вас нет прав для согласования плана", "Сізде жоспарды бекітуге рұқсат жоқ"),
    PLAN_FINAL_APPROVE("Финальное утверждение доступно только зам. департамента", "Түпкілікті бекіту құқығы тек бөлім басшысының орынбасарында ғана бар"),
    PLAN_OUT_OF_APPROVE("План ещё не согласован и недоступен для просмотра", "Жоспар әлі бекітілген жоқ және оны қарап шығу мүмкін емес"),
    OWNER_ONLY("Функция доступна только владельцу дела", "Бұл функция тек іс иесіне қолжетімді"),
    USER_ONLY("У вас нет доступа к этому делу", "Бұл іске қолжетімділігіңіз жоқ"),
    USER_WITHOUT_REGION("У пользователя нет регионов", "Пайдаланушыда аймақтар жоқ"),
    USER_WITHOUT_PROFESSION("У пользователя нет профессии", "Пайдаланушыда кәсіп жоқ"),
    USER_OUT_OF_REGION("Этот пользователь не принадлежит вашему региону", "Бұл пайдаланушы сіздің аймағыңызға жатпайды"),
    APPEAL_OUT_OF_REGION("Это обращение не принадлежит вашему региону", "Бұл өтінім сіздің аймағыңызға жатпайды"),
    CASE_OUT_OF_REGION("Это дело не принадлежит вашему региону", "Бұл іс сіздің аймағыңызға жатпайды"),
    ADMIN_NO_REGION("У администратора нет регионов", "Әкімшіде аймақтар жоқ");

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
