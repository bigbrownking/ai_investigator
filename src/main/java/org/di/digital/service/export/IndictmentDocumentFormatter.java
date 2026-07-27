package org.di.digital.service.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class IndictmentDocumentFormatter extends BaseDocumentFormatter {

    // RU + KZ inline-заголовки
    private static final Pattern INLINE_HEADER = Pattern.compile(
            "^(.*?[\\.,;:\\s])(" +
                    "РЕЗОЛЮТИВНАЯ ЧАСТЬ:|Список обвинения:|Список защиты:|" +
                    "Справка по материалам досудебного расследования:|" +
                    "Айыптау тізімі:|Қорғау тізімі:|" +
                    "Сотқа дейінгі тергеу материалдары бойынша анықтама:" +
                    ")\\s*$");

    private static final Set<String> SECTION_HEADERS = Set.of(
            "РЕЗОЛЮТИВНАЯ ЧАСТЬ:",
            "Справка по материалам досудебного расследования:",
            // KZ
            "ҚОРЫТЫНДЫ БӨЛІМ:",
            "Сотқа дейінгі тергеу материалдары бойынша анықтама:"
    );

    private static final Set<String> LIST_HEADERS = Set.of(
            "Список обвинения:",
            "Список защиты:",
            // KZ
            "Айыптау тізімі:",
            "Қорғау тізімі:"
    );

    private static final Set<String> SPRAVKA_HEADERS = Set.of("СПРАВКА", "АНЫҚТАМА");

    private static final String[] GLUED_HEADERS = {
            "Список лиц, подлежащих вызову",
            "Список обвинения:",
            "Список защиты:",
            "СПРАВКА",
            // KZ
            "Сот отырысына шақырылуға жататын адамдардың тізімі",
            "Айыптау тізімі:",
            "Қорғау тізімі:",
            "АНЫҚТАМА"
    };

    public byte[] generate(List<Map<String, Object>> sections) throws IOException {
        if (sections == null || sections.isEmpty()) {
            throw new IllegalStateException("Обвинительный акт пуст");
        }

        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc);

            for (Map<String, Object> section : sections) {
                String text = (String) section.get("text");
                if (text == null || text.isBlank()) continue;
                DocumentDictionary dict = DocumentDictionary.detect(text);
                renderSection(doc, text, dict);
            }

            return writeDocument(doc);
        }
    }

    private void renderSection(XWPFDocument doc, String text, DocumentDictionary dict) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = splitGluedHeaders(normalized);
        String[] paragraphs = normalized.split("\\n");

        for (int i = 0; i < paragraphs.length; i++) {
            String clean = stripStars(paragraphs[i].trim());
            if (clean.isEmpty()) continue;

            if (isIndictmentTitle(clean, dict)) {
                formatTitle(doc.createParagraph(), clean);
            } else if (isDepartmentLine(clean, dict)) {
                i = handleHeaderDateLine(paragraphs, i, doc, clean, dict);
                addEmptyLine(doc);
            } else if (isCity(clean, dict)) {
                i = handleCityDateLine(paragraphs, i, doc, clean, dict);
                addEmptyLine(doc);
            } else if (isSubjectParagraph(clean, dict)) {
                formatSubjectParagraph(doc.createParagraph(), clean);
                addEmptyLine(doc);
            } else if (SPRAVKA_HEADERS.contains(clean)) {
                addEmptyLine(doc);
                formatSectionHeader(doc.createParagraph(), clean);
            } else if (isMovementSubtitle(clean, dict)) {
                formatSubtitleBold(doc.createParagraph(), clean);
                addEmptyLine(doc);
            } else if (isCallListHeader(clean, dict)) {
                addEmptyLine(doc);
                formatSectionHeader(doc.createParagraph(), clean);
                addEmptyLine(doc);
            } else if (LIST_HEADERS.contains(clean)) {
                addEmptyLine(doc);
                formatListHeader(doc.createParagraph(), clean);
                addEmptyLine(doc);
            } else if (SECTION_HEADERS.contains(clean)) {
                addEmptyLine(doc);
                formatSectionHeader(doc.createParagraph(), clean);
                addEmptyLine(doc);
            } else if (containsInlineHeader(clean)) {
                handleTrailingHeader(doc, clean, INLINE_HEADER);
            } else if (isInvestigatorSignatureBlock(clean, paragraphs, i, dict)) {
                addEmptyLine(doc);
                addEmptyLine(doc);
                i = handleInvestigatorSignatureBlock(paragraphs, i, doc, dict);
            } else {
                formatRegularParagraph(doc.createParagraph(), clean);
            }
        }
    }

    private boolean isIndictmentTitle(String clean, DocumentDictionary dict) {
        return clean.equals(dict.indictmentTitle)
                || clean.equals("Обвинительный акт") || clean.equals("Айыптау актісі");
    }

    private boolean isSubjectParagraph(String text, DocumentDictionary dict) {
        for (String p : dict.subjectPrefixes) {
            if (text.startsWith(p)) return true;
        }
        return false;
    }

    protected boolean isDepartmentLine(String text, DocumentDictionary dict) {
        for (String p : dict.departmentPrefixes) {
            if (text.startsWith(p)) return true;
        }
        // общие для обоих языков
        return text.startsWith("Департамент")
                || text.startsWith("Агентство")
                || text.startsWith("Қаржылық Мониторинг Агенттігінің")
                || text.startsWith("Қаржы мониторингі агенттігі");
    }

    private boolean isMovementSubtitle(String clean, DocumentDictionary dict) {
        for (String m : dict.movementSubtitleMarkers) {
            if (clean.startsWith(m)) return true;
        }
        return clean.startsWith("о движении уголовного дела");
    }

    private boolean isCallListHeader(String clean, DocumentDictionary dict) {
        for (String p : dict.callListPrefixes) {
            if (clean.startsWith(p)) return true;
        }
        return clean.startsWith("Список лиц, подлежащих вызову")
                || clean.startsWith("Сот отырысына шақырылуға жататын адамдардың тізімі");
    }

    private String splitGluedHeaders(String text) {
        for (String header : GLUED_HEADERS) {
            text = text.replaceAll("(?<!\n)(" + Pattern.quote(header) + ")", "\n$1");
        }
        return text;
    }

    private boolean containsInlineHeader(String text) {
        if (SECTION_HEADERS.contains(text) || LIST_HEADERS.contains(text)) return false;
        return INLINE_HEADER.matcher(text).matches();
    }
}