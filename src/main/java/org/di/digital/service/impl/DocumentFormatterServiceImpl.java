package org.di.digital.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.service.DocumentFormatterService;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DocumentFormatterServiceImpl implements DocumentFormatterService {

    /** Заголовки УСТАНОВИЛ:/ПОСТАНОВИЛ: в конце абзаца */
    private static final Pattern QUALIFICATION_INLINE_HEADER =
            Pattern.compile("^(.*?[\\.,;:\\s])(УСТАНОВИЛ:|ПОСТАНОВИЛ:)\\s*$");

    /** Заголовки разделов обвинительного акта в конце абзаца */
    private static final Pattern INDICTMENT_INLINE_HEADER =
            Pattern.compile("^(.*?[\\.,;:\\s])(РЕЗОЛЮТИВНАЯ ЧАСТЬ:|Список обвинения:|Список защиты:|Справка по материалам досудебного расследования:)\\s*$");

    @Override
    public byte[] generateQualificationDocument(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            setPageMargins(document);
            String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
            String[] paragraphs = normalized.split("\\n");

            for (int i = 0; i < paragraphs.length; i++) {
                String trimmedText = paragraphs[i].trim();
                String cleanText = stripStars(trimmedText);

                if (cleanText.isEmpty()) {
                    continue;
                }

                if (isQualificationTitle(cleanText)) {
                    XWPFParagraph p = document.createParagraph();
                    formatTitle(p, cleanText);
                } else if (isQualificationSubtitle(cleanText)) {
                    XWPFParagraph p = document.createParagraph();
                    formatSubtitle(p, cleanText);
                    addEmptyLine(document); // Пустая строка после подзаголовка
                } else if (isCity(cleanText)) {
                    i = handleCityDateLine(paragraphs, i, document, cleanText);
                    addEmptyLine(document); // Пустая строка после даты
                } else if (isQualificationSectionHeader(cleanText)) {
                    addEmptyLine(document); // Пустая строка перед заголовком секции
                    XWPFParagraph p = document.createParagraph();
                    formatSectionHeader(p, cleanText);
                    addEmptyLine(document); // Пустая строка после заголовка секции
                } else if (containsTrailingQualificationHeader(cleanText)) {
                    handleTrailingHeader(document, cleanText, QUALIFICATION_INLINE_HEADER);
                } else if (isInvestigatorSignatureBlock(cleanText, paragraphs, i)) {
                    // *** ИСПРАВЛЕНО: 2 пустые строки ПЕРЕД подписью ***
                    addEmptyLine(document);
                    addEmptyLine(document);
                    i = handleInvestigatorSignatureBlock(paragraphs, i, document);
                } else {
                    XWPFParagraph p = document.createParagraph();
                    formatRegularParagraph(p, cleanText);
                }
            }

            return writeDocument(document);
        }
    }

    @Override
    public byte[] generateIndictmentDocument(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            setPageMargins(document);
            String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
            String[] paragraphs = normalized.split("\\n");

            for (int i = 0; i < paragraphs.length; i++) {
                String trimmedText = paragraphs[i].trim();
                String cleanText = stripStars(trimmedText);

                if (cleanText.isEmpty()) {
                    continue;
                }

                if (isIndictmentTitle(cleanText)) {
                    XWPFParagraph p = document.createParagraph();
                    formatTitle(p, cleanText);
                } else if (isCity(cleanText)) {
                    i = handleCityDateLine(paragraphs, i, document, cleanText);
                    addEmptyLine(document);
                } else if (isIndictmentSectionHeader(cleanText)) {
                    addEmptyLine(document);
                    XWPFParagraph p = document.createParagraph();
                    formatSectionHeader(p, cleanText);
                    addEmptyLine(document);
                } else if (containsTrailingIndictmentHeader(cleanText)) {
                    handleTrailingHeader(document, cleanText, INDICTMENT_INLINE_HEADER);
                } else if (isInvestigatorSignatureBlock(cleanText, paragraphs, i)) {
                    // *** ИСПРАВЛЕНО: 2 пустые строки ПЕРЕД подписью ***
                    addEmptyLine(document);
                    addEmptyLine(document);
                    i = handleInvestigatorSignatureBlock(paragraphs, i, document);
                } else {
                    XWPFParagraph p = document.createParagraph();
                    formatRegularParagraph(p, cleanText);
                }
            }

            return writeDocument(document);
        }
    }

    // ─── Detectors ────────────────────────────────────────────────────────────

    private String stripStars(String text) {
        return text.replace("*", "").trim();
    }

    private boolean isQualificationTitle(String text) {
        return text.equals("ПОСТАНОВЛЕНИЕ");
    }

    private boolean isQualificationSubtitle(String text) {
        return text.startsWith("о квалификации");
    }

    private boolean isIndictmentTitle(String text) {
        return text.equals("Обвинительный акт");
    }

    private boolean isCity(String text) {
        return text.startsWith("Составлен") ||
                text.startsWith("г.") ||
                text.startsWith("г ");
    }

    private boolean isDate(String text) {
        return text.contains("года") && (
                text.matches(".*\\d{1,2}\\s+[\\p{L}\\p{M}]+\\s+\\d{4}.*") ||
                        (text.contains("«") && text.contains("»"))
        );
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

    /** Строка заканчивается на УСТАНОВИЛ:/ПОСТАНОВИЛ: */
    private boolean containsTrailingQualificationHeader(String text) {
        if (isQualificationSectionHeader(text)) return false;
        return QUALIFICATION_INLINE_HEADER.matcher(text).matches();
    }

    private boolean containsTrailingIndictmentHeader(String text) {
        if (isIndictmentSectionHeader(text)) return false;
        return INDICTMENT_INLINE_HEADER.matcher(text).matches();
    }

    /**
     * Блок подписи следователя — строка начинается со "Следователь"/"Дознаватель"/"Старший следователь"
     * и до конца документа осталось мало непустых строк.
     */
    private boolean isInvestigatorSignatureBlock(String text, String[] paragraphs, int currentIndex) {
        if (!(text.startsWith("Следователь") || text.startsWith("Дознаватель") || text.startsWith("Старший следователь"))) {
            return false;
        }
        if (text.contains("рассмотрев") || text.contains("УСТАНОВИЛ") || text.contains("допросил")) {
            return false;
        }
        int nonEmptyAhead = 0;
        for (int j = currentIndex + 1; j < paragraphs.length; j++) {
            String p = stripStars(paragraphs[j].trim());
            if (!p.isEmpty()) nonEmptyAhead++;
        }
        return nonEmptyAhead <= 5;
    }

    // ─── Handlers ─────────────────────────────────────────────────────────────

    /**
     * Город слева, дата справа на одной строке. Ищем дату в следующих непустых строках.
     */
    private int handleCityDateLine(String[] paragraphs, int currentIndex,
                                   XWPFDocument document, String city) {
        int dateIndex = -1;
        String dateText = "";
        for (int j = currentIndex + 1; j < paragraphs.length; j++) {
            String next = stripStars(paragraphs[j].trim());
            if (next.isEmpty()) {
                continue;
            }
            if (isDate(next)) {
                dateIndex = j;
                dateText = next;
            }
            // Первая непустая строка либо дата, либо нет — в любом случае выходим
            break;
        }

        if (dateIndex != -1) {
            formatCityDateLine(document, city, dateText);
            return dateIndex;
        } else {
            XWPFParagraph paragraph = document.createParagraph();
            formatRegularParagraph(paragraph, city);
            return currentIndex;
        }
    }

    /**
     * Разделяет строку вида "...рассмотрев материалы дела №..., УСТАНОВИЛ:"
     * на основной абзац и центрированный жирный заголовок.
     */
    private void handleTrailingHeader(XWPFDocument document, String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.matches()) {
            String body = m.group(1).trim();
            if (body.endsWith(",")) {
                body = body.substring(0, body.length() - 1).trim();
            }
            String header = m.group(2).trim();

            if (!body.isEmpty()) {
                XWPFParagraph bodyPara = document.createParagraph();
                formatRegularParagraph(bodyPara, body);
            }
            addEmptyLine(document); // Пустая строка перед заголовком
            XWPFParagraph headerPara = document.createParagraph();
            formatSectionHeader(headerPara, header);
            addEmptyLine(document); // Пустая строка после заголовка
        } else {
            XWPFParagraph p = document.createParagraph();
            formatRegularParagraph(p, text);
        }
    }

    /**
     * Блок подписи: строки подписи (без дополнительных отступов, они добавляются снаружи).
     * *** ИСПРАВЛЕНО: убраны addEmptyLine отсюда - они теперь добавляются ПЕРЕД вызовом этого метода ***
     */
    private int handleInvestigatorSignatureBlock(String[] paragraphs, int currentIndex,
                                                 XWPFDocument document) {
        // Собираем все строки подписи
        java.util.List<String> signatureLines = new java.util.ArrayList<>();
        int lastIndex = currentIndex;

        for (int j = currentIndex; j < paragraphs.length; j++) {
            String line = stripStars(paragraphs[j].trim());
            if (line.isEmpty()) continue;
            signatureLines.add(line);
            lastIndex = j;
        }

        if (signatureLines.isEmpty()) {
            return lastIndex;
        }

        // Проверяем, нужно ли объединить последние две строки
        // Если предпоследняя строка заканчивается на звание (майор СЭР, подполковник ЮС и т.д.)
        // А последняя - это ФИО
        if (signatureLines.size() >= 2) {
            String secondLast = signatureLines.get(signatureLines.size() - 2);
            String last = signatureLines.get(signatureLines.size() - 1);

            // Проверяем: предпоследняя заканчивается на звание, последняя - это ФИО
            if (endsWithRank(secondLast) && isFIO(last)) {
                // Объединяем последние две строки
                String combined = secondLast + " " + last;

                // Выводим все строки кроме последних двух
                for (int i = 0; i < signatureLines.size() - 2; i++) {
                    formatSignatureLine(document, signatureLines.get(i));
                }

                // Выводим объединенную строку с табуляцией
                formatSignatureLineWithTab(document, combined);

                return lastIndex;
            }
        }

        // Обычная логика: все строки кроме последней - слева, последняя - с табуляцией
        for (int i = 0; i < signatureLines.size() - 1; i++) {
            formatSignatureLine(document, signatureLines.get(i));
        }

        // Последняя строка - с табуляцией (звание слева, ФИО справа)
        String lastLine = signatureLines.get(signatureLines.size() - 1);
        formatSignatureLineWithTab(document, lastLine);

        return lastIndex;
    }

    /**
     * Проверяет, заканчивается ли строка на звание/должность
     * (майор СЭР, подполковник ЮС, старший следователь, следователь, дознаватель)
     */
    private boolean endsWithRank(String text) {
        // Звания с СЭР/ЮС
        if (text.matches(".*(?:майор|подполковник|полковник|капитан|лейтенант)\\s+(?:СЭР|ЮС)\\s*$")) {
            return true;
        }
        // Звания с полным названием службы (майор службы экономических расследований и т.д.)
        if (text.matches(".*(?:майор|подполковник|полковник|капитан|лейтенант)\\s+службы\\s+.+\\s*$")) {
            return true;
        }
        // Должности следователя/дознавателя
        return text.matches(".*(?:старший следователь|следователь|дознаватель)\\s*$");
    }

    /**
     * Проверяет, является ли строка ФИО (Фамилия И.О. или И.О. Фамилия)
     */
    private boolean isFIO(String text) {
        // Формат: Фамилия И.О.
        if (text.matches("^[А-ЯЁ][а-яё]+\\s+[А-ЯЁ]\\.[А-ЯЁ]\\.\\s*$")) {
            return true;
        }
        // Формат: И.О. Фамилия
        return text.matches("^[А-ЯЁ]\\.[А-ЯЁ]\\.\\s+[А-ЯЁ][а-яё]+\\s*$");
    }

    // ─── Formatters ───────────────────────────────────────────────────────────

    private void formatTitle(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setIndentationFirstLine(0);
        setLineSpacing(paragraph);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(16);
        run.setFontFamily("Times New Roman");
    }

    private void formatSubtitle(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setIndentationFirstLine(0);
        setLineSpacing(paragraph);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(14);
        run.setFontFamily("Times New Roman");
    }

    /**
     * Город слева, дата справа на ОДНОЙ строке с табуляцией.
     */
    private void formatCityDateLine(XWPFDocument document, String city, String date) {
        XWPFParagraph para = document.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setSpacingBefore(0);
        para.setSpacingAfter(0);
        setLineSpacing(para);

        // Добавляем табуляцию справа для даты
        CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTTabs tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        CTTabStop tabStop = tabs.addNewTab();
        tabStop.setVal(STTabJc.RIGHT);
        // Позиция табуляции справа (ширина страницы A4 минус правое поле)
        tabStop.setPos(BigInteger.valueOf(9360)); // ~16.5см от левого края

        // Город (слева, жирный)
        XWPFRun cityRun = para.createRun();
        cityRun.setBold(true);
        cityRun.setText(city);
        cityRun.setFontFamily("Times New Roman");
        cityRun.setFontSize(14);

        // Табуляция
        cityRun.addTab();

        // Дата (справа, жирная)
        XWPFRun dateRun = para.createRun();
        dateRun.setBold(true);
        dateRun.setText(date);
        dateRun.setFontFamily("Times New Roman");
        dateRun.setFontSize(14);
    }

    private void formatSectionHeader(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setIndentationFirstLine(0);
        setLineSpacing(paragraph);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(14);
        run.setFontFamily("Times New Roman");
    }

    private void formatRegularParagraph(XWPFParagraph paragraph, String text) {
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        // ИСПРАВЛЕНО: 36pt вместо 720 twips (50.4pt)
        paragraph.setIndentationFirstLine(convertPtToTwips(36));
        setLineSpacing(paragraph);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(14);
        run.setFontFamily("Times New Roman");
    }

    private void formatSignatureLine(XWPFDocument document, String text) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        p.setIndentationFirstLine(0);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("Times New Roman");
        r.setFontSize(14);
    }

    /**
     * Форматирует последнюю строку подписи с табуляцией для ФИО справа.
     * Например: "старший следователь\t\t\t\t\tК.К. Жайлаубаев"
     * Разделяем строку на: всё до последнего ФИО (слева) и ФИО (справа)
     */
    private void formatSignatureLineWithTab(XWPFDocument document, String text) {
        XWPFParagraph para = document.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setIndentationFirstLine(0);
        setLineSpacing(para);

        // Добавляем табуляцию справа
        CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTTabs tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        CTTabStop tabStop = tabs.addNewTab();
        tabStop.setVal(STTabJc.RIGHT);
        tabStop.setPos(BigInteger.valueOf(9360));

        // Ищем ФИО в конце строки
        // Формат 1: Фамилия И.О.
        Pattern fioPattern1 = Pattern.compile("^(.+?)\\s+([А-ЯЁ][а-яё]+\\s+[А-ЯЁ]\\.[А-ЯЁ]\\.)$");
        // Формат 2: И.О. Фамилия
        Pattern fioPattern2 = Pattern.compile("^(.+?)\\s+([А-ЯЁ]\\.[А-ЯЁ]\\.\\s+[А-ЯЁ][а-яё]+)$");

        Matcher matcher1 = fioPattern1.matcher(text);
        Matcher matcher2 = fioPattern2.matcher(text);

        if (matcher1.matches()) {
            String leftPart = matcher1.group(1).trim();  // звание, должность и т.д.
            String fio = matcher1.group(2).trim();        // ФИО

            // Левая часть (звание и т.д.)
            XWPFRun leftRun = para.createRun();
            leftRun.setText(leftPart);
            leftRun.setFontFamily("Times New Roman");
            leftRun.setFontSize(14);

            // Табуляция
            leftRun.addTab();

            // ФИО справа
            XWPFRun fioRun = para.createRun();
            fioRun.setText(fio);
            fioRun.setFontFamily("Times New Roman");
            fioRun.setFontSize(14);
        } else if (matcher2.matches()) {
            String leftPart = matcher2.group(1).trim();  // звание, должность и т.д.
            String fio = matcher2.group(2).trim();        // ФИО

            // Левая часть (звание и т.д.)
            XWPFRun leftRun = para.createRun();
            leftRun.setText(leftPart);
            leftRun.setFontFamily("Times New Roman");
            leftRun.setFontSize(14);

            // Табуляция
            leftRun.addTab();

            // ФИО справа
            XWPFRun fioRun = para.createRun();
            fioRun.setText(fio);
            fioRun.setFontFamily("Times New Roman");
            fioRun.setFontSize(14);
        } else {
            // Если паттерн не подошел, просто выводим как есть
            XWPFRun run = para.createRun();
            run.setText(text);
            run.setFontFamily("Times New Roman");
            run.setFontSize(14);
        }
    }

    // ─── Page setup ───────────────────────────────────────────────────────────

    private void setPageMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(567));     // 1 см
        pageMar.setBottom(BigInteger.valueOf(567));  // 1 см
        pageMar.setLeft(BigInteger.valueOf(1134));   // 2 см
        pageMar.setRight(BigInteger.valueOf(567));   // 1 см
    }

    private void setLineSpacing(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();

        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setLineRule(STLineSpacingRule.AUTO);
        spacing.setLine(BigInteger.valueOf(360)); // 1.5 интервал
        spacing.setBefore(BigInteger.valueOf(0)); // Перед: 0 пт
        spacing.setAfter(BigInteger.valueOf(0));  // После: 0 пт

        // "Не добавлять интервал между абзацами одного стиля"
        if (!pPr.isSetContextualSpacing()) {
            pPr.addNewContextualSpacing();
        }
    }

    private void addEmptyLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setFontSize(14);
        r.setFontFamily("Times New Roman");
        r.setText("");
    }

    /**
     * Конвертирует пункты (pt) в twips (1/20 пункта).
     * Apache POI использует twips для измерения отступов.
     */
    private int convertPtToTwips(double pt) {
        return (int) (pt * 20);
    }

    // ─── Output ───────────────────────────────────────────────────────────────

    private byte[] writeDocument(XWPFDocument document) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.write(outputStream);
        return outputStream.toByteArray();
    }
}