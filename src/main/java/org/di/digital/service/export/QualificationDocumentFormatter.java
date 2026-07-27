package org.di.digital.service.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class QualificationDocumentFormatter extends BaseDocumentFormatter {

    // RU + KZ inline-заголовки в конце строки
    private static final Pattern INLINE_HEADER = Pattern.compile(
            "^(.*?[\\.,;:\\s])(УСТАНОВИЛ:|ПОСТАНОВИЛ:|АНЫҚТАДЫМ:|ҚАУЛЫ ЕТТІМ:)\\s*$");

    public byte[] generate(List<Map<String, Object>> sections) throws IOException {
        if (sections == null || sections.isEmpty()) {
            throw new IllegalStateException("Квалификация пуста");
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
        String[] paragraphs = text.replace("\r\n", "\n").replace("\r", "\n").split("\\n");

        for (int i = 0; i < paragraphs.length; i++) {
            String clean = stripStars(paragraphs[i].trim());
            if (clean.isEmpty()) continue;

            if (isTitle(clean, dict)) {
                formatTitle(doc.createParagraph(), clean);
            } else if (isSubtitle(clean, dict)) {
                formatSubtitle(doc.createParagraph(), clean);
                addEmptyLine(doc);
            } else if (isCity(clean, dict)) {
                i = handleCityDateLine(paragraphs, i, doc, clean, dict);
                addEmptyLine(doc);
            } else if (isStandaloneHeader(clean, dict)) {
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

    private boolean isTitle(String clean, DocumentDictionary dict) {
        return clean.equals(dict.qualificationTitle)
                || clean.equals("ПОСТАНОВЛЕНИЕ") || clean.equals("ҚАУЛЫ");
    }

    private boolean isSubtitle(String clean, DocumentDictionary dict) {
        for (String m : dict.qualificationSubtitleMarkers) {
            if (clean.startsWith(m) || clean.contains(m)) return true;
        }
        return false;
    }

    private boolean isStandaloneHeader(String clean, DocumentDictionary dict) {
        return dict.establishedHeaders.contains(clean)
                || dict.resolutionHeaders.contains(clean)
                // на случай смешанного языка секций
                || clean.equals("УСТАНОВИЛ:") || clean.equals("ПОСТАНОВИЛ:")
                || clean.equals("АНЫҚТАДЫМ:") || clean.equals("ҚАУЛЫ ЕТТІМ:");
    }

    private boolean containsInlineHeader(String text) {
        if (text.equals("УСТАНОВИЛ:") || text.equals("ПОСТАНОВИЛ:")
                || text.equals("АНЫҚТАДЫМ:") || text.equals("ҚАУЛЫ ЕТТІМ:")) return false;
        return INLINE_HEADER.matcher(text).matches();
    }
}