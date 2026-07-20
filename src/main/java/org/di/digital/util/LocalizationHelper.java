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
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LocalizationHelper {
    private static final Map<String, String> REGION_KZ_TO_RU = Map.ofEntries(
            Map.entry("Ақмола", "Акмолинская область"),
            Map.entry("Ақтөбе", "Актюбинская область"),
            Map.entry("Алматы облысы", "Алматинская область"),
            Map.entry("Алматы қаласы", "город Алматы"),
            Map.entry("Астана қаласы", "город Астана"),
            Map.entry("Атырау", "Атырауская область"),
            Map.entry("Шығыс Қазақстан", "Восточно-Казахстанская область"),
            Map.entry("Жамбыл", "Жамбылская область"),
            Map.entry("Батыс Қазақстан", "Западно-Казахстанская область"),
            Map.entry("Қарағанды", "Карагандинская область"),
            Map.entry("Қостанай", "Костанайская область"),
            Map.entry("Қызылорда", "Кызылординская область"),
            Map.entry("Маңғыстау", "Мангистауская область"),
            Map.entry("Павлодар", "Павлодарская область"),
            Map.entry("Солтүстік Қазақстан", "Северо-Казахстанская область"),
            Map.entry("Түркістан", "Туркестанская область"),
            Map.entry("Шымкент", "город Шымкент"),
            Map.entry("Абай", "Абайская область"),
            Map.entry("Жетісу", "Жетысуская область"),
            Map.entry("Ұлытау", "Улытауская область")
    );

    private static final Map<String, String> REGION_KZ_TO_KZ_SHORT = Map.ofEntries(
            Map.entry("Ақмола", "Ақмола облысы"),
            Map.entry("Ақтөбе", "Ақтөбе облысы"),
            Map.entry("Алматы облысы", "Алматы облысы"),
            Map.entry("Алматы қаласы", "Алматы қаласы"),
            Map.entry("Астана қаласы", "Астана қаласы"),
            Map.entry("Атырау", "Атырау облысы"),
            Map.entry("Шығыс Қазақстан", "Шығыс Қазақстан облысы"),
            Map.entry("Жамбыл", "Жамбыл облысы"),
            Map.entry("Батыс Қазақстан", "Батыс Қазақстан облысы"),
            Map.entry("Қарағанды", "Қарағанды облысы"),
            Map.entry("Қостанай", "Қостанай облысы"),
            Map.entry("Қызылорда", "Қызылорда облысы"),
            Map.entry("Маңғыстау", "Маңғыстау облысы"),
            Map.entry("Павлодар", "Павлодар облысы"),
            Map.entry("Солтүстік Қазақстан", "Солтүстік Қазақстан облысы"),
            Map.entry("Түркістан", "Түркістан облысы"),
            Map.entry("Шымкент", "Шымкент қаласы"),
            Map.entry("Абай", "Абай облысы"),
            Map.entry("Жетісу", "Жетісу облысы"),
            Map.entry("Ұлытау", "Ұлытау облысы")
    );

    private static final Map<String, String> DOC_TYPE_TO_KZ = Map.ofEntries(
            Map.entry("Удостоверение личности", "Жеке куәлік"),
            Map.entry("Паспорт", "Паспорт"),
            Map.entry("Вид на жительство", "Тұруға ықтиярхат"),
            Map.entry("Свидетельство о рождении", "Туу туралы куәлік")
    );

    public String extractRegionShortName(String fullDepartmentName, UserSettingsLanguage language) {
        if (!StringUtils.hasText(fullDepartmentName)) return fullDepartmentName;

        for (Map.Entry<String, String> entry : REGION_KZ_TO_RU.entrySet()) {
            if (fullDepartmentName.contains(entry.getKey())) {
                return switch (language) {
                    case KZ -> REGION_KZ_TO_KZ_SHORT.getOrDefault(entry.getKey(), entry.getKey());
                    case RU, EN -> entry.getValue();
                };
            }
        }

        return fullDepartmentName;
    }

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


        if (rest.contains("департаменті") || rest.contains("Департаменті")) {
            return prefix + rest
                    .replace("департаменті", "департаментінің")
                    .replace("Департаменті", "Департаментінің");
        }

        if (rest.contains("мекемесі") || rest.contains("Мекемесі")) {
            return prefix + rest
                    .replace("мекемесі", "мекемесінің")
                    .replace("Мекемесі", "Мекемесінің");
        }

        if (rest.endsWith("Тергеу бөлімі")) {
            return prefix + rest
                    .replace("Тергеу бөлімі", "Тергеу бөлімінің");
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

    public String localizeDocumentType(String documentType, String language) {
        if (!StringUtils.hasText(documentType)) return documentType;
        String normalized = toTitleCase(documentType);
        return "казахском".equals(language)
                ? DOC_TYPE_TO_KZ.getOrDefault(normalized, normalized)
                : normalized;
    }
    public String localizeIssuedWord(String language) {
        return "казахском".equals(language) ? "берген" : "выдано";
    }

    public String localizeIinLabel(String language) {
        return "казахском".equals(language) ? "ЖСН" : "ИИН";
    }

    public String localizeFromWord(String language) {
        return "казахском".equals(language) ? "" : "от";
    }
}