package org.di.digital.service.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.regex.Pattern;

@Slf4j
@Service
public class QualificationDocumentFormatter extends BaseDocumentFormatter {

    private static final Pattern INLINE_HEADER =
            Pattern.compile("^(.*?[\\.,;:\\s])(УСТАНОВИЛ:|ПОСТАНОВИЛ:)\\s*$");

    public byte[] generate(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc);
            String[] paragraphs = text.replace("\r\n", "\n").replace("\r", "\n").split("\\n");

            for (int i = 0; i < paragraphs.length; i++) {
                String clean = stripStars(paragraphs[i].trim());
                if (clean.isEmpty()) continue;

                if (clean.equals("ПОСТАНОВЛЕНИЕ")) {
                    formatTitle(doc.createParagraph(), clean);
                } else if (clean.startsWith("о квалификации")) {
                    formatSubtitle(doc.createParagraph(), clean);
                    addEmptyLine(doc);
                } else if (isCity(clean)) {
                    i = handleCityDateLine(paragraphs, i, doc, clean);
                    addEmptyLine(doc);
                } else if (clean.equals("УСТАНОВИЛ:") || clean.equals("ПОСТАНОВИЛ:")) {
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

    private boolean containsInlineHeader(String text) {
        if (text.equals("УСТАНОВИЛ:") || text.equals("ПОСТАНОВИЛ:")) return false;
        return INLINE_HEADER.matcher(text).matches();
    }
}
