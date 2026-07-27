package org.di.digital.service.export;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Языкозависимые литералы для форматирования документов.
 * Позволяет BaseDocumentFormatter и наследникам работать
 * с русскими и казахскими текстами без хардкода строк.
 */
public class DocumentDictionary {

    // ─── Qualification ──────────────────────────────────────────────────────
    public final String qualificationTitle;        // ПОСТАНОВЛЕНИЕ / ҚАУЛЫ
    public final List<String> qualificationSubtitleMarkers; // startsWith/contains для подзаголовка
    public final List<String> establishedHeaders;  // УСТАНОВИЛ: / АНЫҚТАДЫМ:
    public final List<String> resolutionHeaders;   // ПОСТАНОВИЛ: / ҚАУЛЫ ЕТТІМ:

    // ─── Indictment ─────────────────────────────────────────────────────────
    public final String indictmentTitle;           // Обвинительный акт / Айыптау актісі
    public final List<String> departmentPrefixes;  // Департамент / Қаржылық Мониторинг Агенттігінің ...
    public final List<String> movementSubtitleMarkers; // "о движении уголовного дела" / каз аналог
    public final List<String> spravkaHeaders;      // СПРАВКА / АНЫҚТАМА
    public final List<String> callListPrefixes;    // "Список лиц, подлежащих вызову" / каз
    public final List<String> subjectPrefixes;     // Гражданин/Гражданка / Азамат/Азаматша

    // ─── Общие ──────────────────────────────────────────────────────────────
    public final List<String> investigatorPrefixes;   // должности следователя
    public final List<String> investigatorStopWords;  // слова, означающие "это не подпись"
    public final List<String> positionSplitPoints;    // точки разбиения строки должности
    public final List<String> cityPrefixes;           // префиксы строки города
    public final List<String> citySuffixes;           // суффиксы строки города (қ.)
    public final Pattern datePattern;                 // распознавание даты

    public DocumentDictionary(String qualificationTitle,
                              List<String> qualificationSubtitleMarkers,
                              List<String> establishedHeaders,
                              List<String> resolutionHeaders,
                              String indictmentTitle,
                              List<String> departmentPrefixes,
                              List<String> movementSubtitleMarkers,
                              List<String> spravkaHeaders,
                              List<String> callListPrefixes,
                              List<String> subjectPrefixes,
                              List<String> investigatorPrefixes,
                              List<String> investigatorStopWords,
                              List<String> positionSplitPoints,
                              List<String> cityPrefixes,
                              List<String> citySuffixes,
                              Pattern datePattern) {
        this.qualificationTitle = qualificationTitle;
        this.qualificationSubtitleMarkers = qualificationSubtitleMarkers;
        this.establishedHeaders = establishedHeaders;
        this.resolutionHeaders = resolutionHeaders;
        this.indictmentTitle = indictmentTitle;
        this.departmentPrefixes = departmentPrefixes;
        this.movementSubtitleMarkers = movementSubtitleMarkers;
        this.spravkaHeaders = spravkaHeaders;
        this.callListPrefixes = callListPrefixes;
        this.subjectPrefixes = subjectPrefixes;
        this.investigatorPrefixes = investigatorPrefixes;
        this.investigatorStopWords = investigatorStopWords;
        this.positionSplitPoints = positionSplitPoints;
        this.cityPrefixes = cityPrefixes;
        this.citySuffixes = citySuffixes;
        this.datePattern = datePattern;
    }

    // ─── RU ─────────────────────────────────────────────────────────────────
    public static final DocumentDictionary RU = new DocumentDictionary(
            "ПОСТАНОВЛЕНИЕ",
            List.of("о квалификации"),
            List.of("УСТАНОВИЛ:"),
            List.of("ПОСТАНОВИЛ:"),
            "Обвинительный акт",
            List.of("Департамент", "Агентство"),
            List.of("о движении уголовного дела"),
            List.of("СПРАВКА"),
            List.of("Список лиц, подлежащих вызову"),
            List.of("Гражданин", "Гражданка"),
            List.of("Следователь", "Дознаватель", "Старший следователь",
                    "Заместитель руководителя", "Руководитель"),
            List.of("рассмотрев", "УСТАНОВИЛ", "допросил"),
            List.of("Следственного", "Департамента", "Агентства",
                    "Управления", "отдела", "отдел", "по "),
            List.of("Составлен", "г.", "г ", "город "),
            List.of(),
            Pattern.compile(".*года.*\\d{4}.*|.*\\d{1,2}\\s+[\\p{L}\\p{M}]+\\s+\\d{4}.*|.*«.*».*года.*")
    );

    // ─── KZ ─────────────────────────────────────────────────────────────────
    public static final DocumentDictionary KZ = new DocumentDictionary(
            "ҚАУЛЫ",
            List.of("саралау туралы"),
            List.of("АНЫҚТАДЫМ:"),
            List.of("ҚАУЛЫ ЕТТІМ:"),
            "Айыптау актісі",
            List.of("Қаржылық Мониторинг Агенттігінің", "Қаржы мониторингі агенттігі",
                    "Департамент", "Агенттік", "Экономикалық Тергеп-Тексеру департаменті"),
            List.of("қылмыстық істің қозғалысы туралы"),
            List.of("АНЫҚТАМА"),
            List.of("Сот отырысына шақырылуға жататын адамдардың тізімі"),
            List.of("Азамат", "Азаматша"),
            List.of("Тергеуші", "Аға тергеуші", "Аңықтаушы",
                    "Аса маңызды істер бойынша тергеуші",
                    "Басшының орынбасары", "Басшы"),
            List.of("қарап шығып", "АНЫҚТАДЫМ", "жауап алып"),
            List.of("Департаменті", "департаменті", "Басқармасы", "бөлімі", "бөлім",
                    "Агенттігінің", "Агенттігі", "Тергеу", "бойынша", "по "),
            List.of("қала "),
            List.of("қ.", "қаласы"),
            // каз: "2026 жылғы 25 шілде" / "2026 жыл 25 шілде"
            Pattern.compile(".*жыл.*\\d{4}.*|.*\\d{4}\\s*жыл.*")
    );

    /**
     * Определение языка секции по казахским спецбуквам и характерным заголовкам.
     */
    public static DocumentDictionary detect(String text) {
        if (text == null) return RU;
        if (text.matches("(?s).*[әғқңөұүһі].*")
                || text.contains("ҚАУЛЫ")
                || text.contains("АНЫҚТАДЫМ")
                || text.contains("Айыптау актісі")
                || text.contains("Тергеуші")
                || text.contains("жыл")) {
            return KZ;
        }
        return RU;
    }
}