package org.di.digital.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.service.WordDocumentService;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class WordDocumentServiceImpl implements WordDocumentService {

    @Override
    public byte[] generateQualificationDocument(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            String[] paragraphs = text.split("\\n");

            for (int i = 0; i < paragraphs.length; i++) {
                String trimmedText = paragraphs[i].trim();

                if (trimmedText.isEmpty()) {
                    document.createParagraph();
                    continue;
                }

                XWPFParagraph paragraph = document.createParagraph();

                if (isQualificationTitle(trimmedText)) {
                    formatTitle(paragraph, trimmedText);
                } else if (isQualificationSubtitle(trimmedText)) {
                    formatSubtitle(paragraph, trimmedText);
                } else if (isCity(trimmedText)) {
                    i = handleCityDateLine(paragraphs, i, paragraph, trimmedText);
                } else if (isQualificationSectionHeader(trimmedText)) {
                    formatSectionHeader(paragraph, trimmedText);
                } else {
                    formatRegularParagraph(paragraph, trimmedText);
                }
            }

            return writeDocument(document);
        }
    }

    @Override
    public byte[] generateIndictmentDocument(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            String[] paragraphs = text.split("\\n");

            for (int i = 0; i < paragraphs.length; i++) {
                String trimmedText = paragraphs[i].trim();

                if (trimmedText.isEmpty()) {
                    document.createParagraph();
                    continue;
                }

                XWPFParagraph paragraph = document.createParagraph();

                if (isIndictmentTitle(trimmedText)) {
                    formatTitle(paragraph, trimmedText);
                } else if (isCity(trimmedText)) {
                    i = handleCityDateLine(paragraphs, i, paragraph, trimmedText);
                } else if (isIndictmentSectionHeader(trimmedText)) {
                    formatSectionHeader(paragraph, trimmedText);
                } else {
                    formatRegularParagraph(paragraph, trimmedText);
                }
            }

            return writeDocument(document);
        }
    }

    private boolean isQualificationTitle(String text) {
        return text.equals("ПОСТАНОВЛЕНИЕ");
    }

    private boolean isQualificationSubtitle(String text) {
        return text.startsWith("о квалификации") ||
                text.equals("о квалификации деяния подозреваемого");
    }

    private boolean isIndictmentTitle(String text) {
        return text.equals("Обвинительный акт");
    }

    private boolean isCity(String text) {
        return text.startsWith("Составлен") || text.startsWith("г.");
    }

    private boolean isDate(String text) {
        return text.contains("«") && text.contains("»") && text.contains("года");
    }

    private boolean isQualificationSectionHeader(String text) {
        return text.equals("УСТАНОВИЛ:") || text.equals("ПОСТАНОВИЛ:");
    }

    private boolean isIndictmentSectionHeader(String text) {
        return text.equals("РЕЗОЛЮТИВНАЯ ЧАСТЬ:") ||
                text.equals("Список обвинения:") ||
                text.equals("Список защиты:") ||
                text.equals("Справка по материалам досудебного расследования:");
    }

    private int handleCityDateLine(String[] paragraphs, int currentIndex, XWPFParagraph paragraph, String city) {
        String nextLine = (currentIndex + 1 < paragraphs.length) ? paragraphs[currentIndex + 1].trim() : "";
        if (isDate(nextLine)) {
            formatCityDateLine(paragraph, city, nextLine);
            return currentIndex + 1;
        } else {
            formatRegularParagraph(paragraph, city);
            return currentIndex;
        }
    }

    private void formatTitle(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(16);
        run.setFontFamily("Times New Roman");
    }

    private void formatSubtitle(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(14);
        run.setFontFamily("Times New Roman");
    }

    private void formatCityDateLine(XWPFParagraph paragraph, String city, String date) {
        paragraph.setAlignment(ParagraphAlignment.LEFT);

        if (paragraph.getCTP().getPPr() == null) {
            paragraph.getCTP().addNewPPr();
        }

        CTTabStop tabStop = paragraph.getCTP().getPPr().addNewTabs().addNewTab();
        tabStop.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.RIGHT);
        tabStop.setPos(java.math.BigInteger.valueOf(9072));

        XWPFRun cityRun = paragraph.createRun();
        cityRun.setText(city);
        cityRun.setFontSize(12);
        cityRun.setFontFamily("Times New Roman");

        XWPFRun tabRun = paragraph.createRun();
        tabRun.addTab();

        XWPFRun dateRun = paragraph.createRun();
        dateRun.setText(date);
        dateRun.setFontSize(12);
        dateRun.setFontFamily("Times New Roman");
    }

    private void formatSectionHeader(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(12);
        run.setFontFamily("Times New Roman");
    }

    private void formatRegularParagraph(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setIndentationFirstLine(720);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(12);
        run.setFontFamily("Times New Roman");
    }

    private byte[] writeDocument(XWPFDocument document) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.write(outputStream);
        return outputStream.toByteArray();
    }
}