package org.di.digital.util;

import org.di.digital.model.Localizable;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.springframework.stereotype.Component;

@Component
public class LocalizationHelper {

    public String getLocalizedName(Localizable entity, UserSettingsLanguage language) {
        if (entity == null) {
            return null;
        }
        return switch (language) {
            case KZ -> entity.getKzName();
            case EN, RU -> entity.getRuName();
        };
    }
    public String getLocalizedInterrogation(Localizable entity, String language){
        if(entity == null){
            return null;
        }
        return switch (language){
            case "казахском" -> entity.getKzName();
            default -> entity.getRuName();
        };
    }
}