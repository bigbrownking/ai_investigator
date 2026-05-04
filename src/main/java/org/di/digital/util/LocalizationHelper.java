package org.di.digital.util;

import org.di.digital.model.Localizable;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
    public String getGenitive(String department) {
        if (!StringUtils.hasText(department)) return department;

        String prefix = "";
        String rest = department.trim();

        if (rest.matches("^\\d+\\s+.*")) {
            int spaceIdx = rest.indexOf(' ');
            prefix = rest.substring(0, spaceIdx + 1);
            rest = rest.substring(spaceIdx + 1);
        }

        if (rest.equalsIgnoreCase("Следственное управление") ||
                rest.equalsIgnoreCase("Следственного управления")) {
            return prefix + "Следственного управления";
        }

        if (rest.startsWith("Департамент ") || rest.startsWith("Департамента ")) {
            String after = rest.startsWith("Департамента ")
                    ? rest.substring("Департамента ".length())
                    : rest.substring("Департамент ".length());
            return prefix + "Департамента " + after;
        }

        if (rest.startsWith("Агентство ") || rest.startsWith("Агентства ")) {
            String after = rest.startsWith("Агентства ")
                    ? rest.substring("Агентства ".length())
                    : rest.substring("Агентство ".length());
            return prefix + "Агентства " + after;
        }

        return department;
    }
    public String toTitleCase(String text) {
        if (!StringUtils.hasText(text)) return text;

        return Arrays.stream(text.trim().split("\\s+"))
                .map(word -> word.isEmpty() ? word :
                        Character.toUpperCase(word.charAt(0)) +
                                word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
    public String toCityShortVersion(String text) {
        if (!StringUtils.hasText(text)) return text;

        String formattedName = Arrays.stream(text.trim().split("\\s+"))
                .map(word -> word.isEmpty() ? word :
                        Character.toUpperCase(word.charAt(0)) +
                                word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));

        return "г." + formattedName;
    }
    public String formatToRussianDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return dateStr;

        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", new Locale("ru"));

        if (dateStr.matches("\\d{1,2} .+ \\d{4}.*")) {
            return dateStr;
        }

        for (String pattern : List.of("yyyy-MM-dd", "yyyy/MM/dd", "dd.MM.yyyy")) {
            try {
                LocalDate date = LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(pattern));
                return date.format(outputFormatter);
            } catch (Exception ignored) {}
        }

        return dateStr;
    }
}