package org.di.digital.service.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
@Slf4j
@Service
public class IndictmentDocumentFormatter extends BaseDocumentFormatter {

    private static final Pattern INLINE_HEADER =
            Pattern.compile("^(.*?[\\.,;:\\s])(РЕЗОЛЮТИВНАЯ ЧАСТЬ:|Список обвинения:|Список защиты:|Справка по материалам досудебного расследования:)\\s*$");

    private static final Set<String> SECTION_HEADERS = Set.of(
            "РЕЗОЛЮТИВНАЯ ЧАСТЬ:",
            "Справка по материалам досудебного расследования:"
    );

    private static final Set<String> LIST_HEADERS = Set.of(
            "Список обвинения:",
            "Список защиты:"
    );

    private static final String[] GLUED_HEADERS = {
            "Список лиц, подлежащих вызову",
            "Список обвинения:",
            "Список защиты:",
            "СПРАВКА"
    };

    public byte[] generate(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc);

            String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
            normalized = splitGluedHeaders(normalized);
            String[] paragraphs = normalized.split("\\n");

            for (int i = 0; i < paragraphs.length; i++) {
                String clean = stripStars(paragraphs[i].trim());
                if (clean.isEmpty()) continue;

                if (clean.equals("Обвинительный акт")) {
                    formatTitle(doc.createParagraph(), clean);
                } else if (isCity(clean)) {
                    i = handleCityDateLine(paragraphs, i, doc, clean);
                    addEmptyLine(doc);
                } else if (isSubjectParagraph(clean)) {
                    formatSubjectParagraph(doc.createParagraph(), clean);
                    addEmptyLine(doc);
                } else if (clean.equals("СПРАВКА")) {
                    addEmptyLine(doc);
                    formatSectionHeader(doc.createParagraph(), clean);
                } else if (clean.startsWith("о движении уголовного дела")) {
                    formatSubtitleBold(doc.createParagraph(), clean);
                    addEmptyLine(doc);
                } else if (clean.startsWith("Список лиц, подлежащих вызову")) {
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
                } else if (isInvestigatorSignatureBlock(clean, paragraphs, i)) {
                    addEmptyLine(doc);
                    addEmptyLine(doc);
                    i = handleInvestigatorSignatureBlock(paragraphs, i, doc);
                } else {
                    formatRegularParagraph(doc.createParagraph(), clean);
                }
            }
            return writeDocument(doc);
        }
    }

    private boolean isSubjectParagraph(String text) {
        return text.startsWith("Гражданин") || text.startsWith("Гражданка");
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