package org.di.digital.service.export;

import org.apache.poi.xwpf.usermodel.*;
import org.di.digital.dto.response.CaseInterrogationProtocolResponse;
import org.di.digital.dto.response.CaseInterrogationQAResponse;
import org.di.digital.dto.response.InterrogationTimerSessionResponse;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;
import java.util.List;

/**
 * Базовый строитель документа допроса.
 * Содержит все общие POI-утилиты: шрифты, отступы, таблицы, подписи, Q&A.
 * Русские и казахские сервисы наследуют этот класс.
 */
public abstract class BaseInterrogationDocBuilder {

    protected static final String FONT = "Times New Roman";
    protected static final int    FONT_SIZE = 14;

    // ── Страница ─────────────────────────────────────────────────────────────

    public static void setPageMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(567));    // 1 cm
        pageMar.setBottom(BigInteger.valueOf(567));
        pageMar.setLeft(BigInteger.valueOf(1134));  // 2 cm
        pageMar.setRight(BigInteger.valueOf(567));  // 1 cm
    }

    // ── Межстрочный интервал ─────────────────────────────────────────────────

    protected void setLineSpacing(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setLineRule(STLineSpacingRule.AUTO);
        spacing.setLine(BigInteger.valueOf(240));
        spacing.setBefore(BigInteger.valueOf(0));
        spacing.setAfter(BigInteger.valueOf(0));
        spacing.setAfterLines(BigInteger.valueOf(0));
        spacing.setBeforeLines(BigInteger.valueOf(0));
        if (!pPr.isSetContextualSpacing()) {
            pPr.addNewContextualSpacing();
        }
    }

    // ── Абзацы ───────────────────────────────────────────────────────────────

    protected void addCenteredBoldParagraph(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setText(text);
        r.setFontFamily(FONT);
        r.setFontSize(fontSize);
    }

    protected void addCenteredParagraph(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily(FONT);
        r.setFontSize(fontSize);
    }

    protected void addJustifiedParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(720);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily(FONT);
        r.setFontSize(FONT_SIZE);
    }

    protected void addJustifiedItalicParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(720);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setItalic(true);
        r.setText(text);
        r.setFontFamily(FONT);
        r.setFontSize(FONT_SIZE);
    }

    protected void addEmptyLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(FONT_SIZE);
        r.setText("");
    }

    // ── Строка подписи ───────────────────────────────────────────────────────

    /**
     * Роль: ______  [tab →]  ФИО
     */
    protected void addInlineSignatureLine(XWPFDocument doc, String roleLabel, String fio) {
        addEmptyLine(doc);
        addEmptyLine(doc);
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        setLineSpacing(p);

        XWPFRun r1 = p.createRun();
        r1.setText(roleLabel + ":   ");
        r1.setFontFamily(FONT);
        r1.setFontSize(FONT_SIZE);

        XWPFRun r2 = p.createRun();
        r2.setText("____________________");
        r2.setFontFamily(FONT);
        r2.setFontSize(FONT_SIZE);

        // Правый таб-стоп для ФИО
        CTPPr pPr = p.getCTP().getPPr() != null ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTTabStop tabStop = pPr.addNewTabs().addNewTab();
        tabStop.setVal(STTabJc.RIGHT);
        tabStop.setPos(BigInteger.valueOf(9360));

        XWPFRun r3 = p.createRun();
        r3.addTab();
        r3.setText(fio != null && !fio.isBlank() ? fio : "");
        r3.setFontFamily(FONT);
        r3.setFontSize(FONT_SIZE);
    }

    // ── Блок следователя (нижняя подпись) ───────────────────────────────────

    protected void addInvestigatorBlock(XWPFDocument doc, String labelText,
                                        String profession, String investigatorFio) {
        addEmptyLine(doc);
        addEmptyLine(doc);

        XWPFParagraph label = doc.createParagraph();
        setLineSpacing(label);
        XWPFRun lr = label.createRun();
        lr.setBold(true);
        lr.setText(labelText);
        lr.setFontFamily(FONT);
        lr.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        XWPFParagraph profPara = doc.createParagraph();
        profPara.setAlignment(ParagraphAlignment.LEFT);
        setLineSpacing(profPara);
        XWPFRun pr = profPara.createRun();
        pr.setBold(true);
        pr.setText(safe(profession));
        pr.setFontFamily(FONT);
        pr.setFontSize(FONT_SIZE);
        addEmptyLine(doc);

        XWPFParagraph sigPara = doc.createParagraph();
        sigPara.setAlignment(ParagraphAlignment.BOTH);
        setLineSpacing(sigPara);

        CTPPr pPr = sigPara.getCTP().isSetPPr() ? sigPara.getCTP().getPPr() : sigPara.getCTP().addNewPPr();
        CTTabStop tabStop = pPr.addNewTabs().addNewTab();
        tabStop.setVal(STTabJc.RIGHT);
        tabStop.setPos(BigInteger.valueOf(9360));

        XWPFRun sr = sigPara.createRun();
        sr.setText("___________________________");
        sr.setFontFamily(FONT);
        sr.setFontSize(FONT_SIZE);

        XWPFRun srTab = sigPara.createRun();
        srTab.addTab();
        srTab.setText(safe(investigatorFio));
        srTab.setFontFamily(FONT);
        srTab.setFontSize(FONT_SIZE);

        XWPFParagraph notePara = doc.createParagraph();
        notePara.setAlignment(ParagraphAlignment.LEFT);
        setLineSpacing(notePara);
        XWPFRun nr = notePara.createRun();
        nr.setFontFamily(FONT);
        nr.setFontSize(10);
        nr.setColor("888888");
        nr.setText(getSignatureNote());
    }

    /** Подпись под чертой — переопределяется в подклассах */
    protected abstract String getSignatureNote();

    // ── Таблица персональных данных ──────────────────────────────────────────

    protected void buildDataTable(XWPFDocument doc, String[][] rows) {
        XWPFTable table = doc.createTable(rows.length, 2);
        removeTableBorders(table);

        // Растянуть таблицу на 100% ширины страницы
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();
        CTTblWidth tblW = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setW(BigInteger.valueOf(5000));
        tblW.setType(STTblWidth.PCT);
        // Убрать межячеечный отступ по умолчанию
        if (!tblPr.isSetTblCellSpacing()) {
            CTTblWidth sp = tblPr.addNewTblCellSpacing();
            sp.setW(BigInteger.ZERO);
            sp.setType(STTblWidth.DXA);
        }

        for (int i = 0; i < rows.length; i++) {
            XWPFTableRow row = table.getRow(i);

            XWPFTableCell labelCell = row.getCell(0);
            setCellWidth(labelCell, 3600);
            setCellThinBorder(labelCell);
            setCellPadding(labelCell);
            XWPFRun lr = labelCell.getParagraphs().get(0).createRun();
            lr.setText(rows[i][0]);
            lr.setFontFamily(FONT);
            lr.setFontSize(FONT_SIZE);

            XWPFTableCell valueCell = row.getCell(1);
            setCellWidth(valueCell, 5760);
            setCellThinBorder(valueCell);
            setCellPadding(valueCell);
            XWPFRun vr = valueCell.getParagraphs().get(0).createRun();
            vr.setText(rows[i][1]);
            vr.setFontFamily(FONT);
            vr.setFontSize(FONT_SIZE);
        }
    }

    // ── Блок Q&A ─────────────────────────────────────────────────────────────

    /**
     * @param questionLabel  «Вопрос следователя» / «Тергеушінің сұрағы»
     * @param answerLabel    «Ответ» / «Жауабы»
     * @param noAnswerLabel  «Ответ не получен.» / «Жауап алынбады.»
     */
    protected void addQABlock(XWPFDocument doc,
                              List<CaseInterrogationQAResponse> qaList,
                              String roleLabel, String fio,
                              String questionLabel, String answerLabel, String noAnswerLabel) {
        if (qaList == null || qaList.isEmpty()) return;
        for (CaseInterrogationQAResponse qa : qaList) {
            XWPFParagraph qPara = doc.createParagraph();
            qPara.setAlignment(ParagraphAlignment.BOTH);
            qPara.setIndentationFirstLine(720);
            XWPFRun qRun = qPara.createRun();
            qRun.setBold(true);
            qRun.setText(questionLabel + ": " + safe(qa.getQuestion()));
            qRun.setFontFamily(FONT);
            qRun.setFontSize(FONT_SIZE);

            XWPFParagraph aPara = doc.createParagraph();
            aPara.setAlignment(ParagraphAlignment.BOTH);
            aPara.setIndentationFirstLine(720);
            XWPFRun aRun = aPara.createRun();
            String answer = (qa.getAnswer() != null && !qa.getAnswer().isBlank())
                    ? qa.getAnswer() : noAnswerLabel;
            aRun.setText(answerLabel + ": " + answer);
            aRun.setFontFamily(FONT);
            aRun.setFontSize(FONT_SIZE);

            addEmptyLine(doc);
            addInlineSignatureLine(doc, roleLabel, fio);
            addEmptyLine(doc);
        }
    }

    // ── Шапка: дата слева / город справа ─────────────────────────────────────

    /**
     * Двухколоночная шапка без рамок.
     * @param leftText   текст левой ячейки (дата или город)
     * @param rightText  текст правой ячейки (город или дата)
     */
    protected void addHeaderRow(XWPFDocument doc, String leftText, String rightText) {
        XWPFTable table = doc.createTable(1, 2);
        removeTableBorders(table);

        XWPFTableCell left = table.getRow(0).getCell(0);
        setCellWidth(left, 4826);
        XWPFParagraph lp = left.getParagraphs().get(0);
        lp.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun lr = lp.createRun();
        lr.setBold(true);
        lr.setText(leftText);
        lr.setFontFamily(FONT);
        lr.setFontSize(FONT_SIZE);

        XWPFTableCell right = table.getRow(0).getCell(1);
        setCellWidth(right, 4500);
        XWPFParagraph rp = right.getParagraphs().get(0);
        rp.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun rr = rp.createRun();
        rr.setBold(true);
        rr.setText(rightText);
        rr.setFontFamily(FONT);
        rr.setFontSize(FONT_SIZE);
    }

    /**
     * Блок времени допроса с перерывами (несколько runs с addBreak).
     * Параметры меток локализованы в подклассах.
     */
    protected void addTimeBlock(XWPFDocument doc,
                                List<InterrogationTimerSessionResponse> sessions,
                                java.time.LocalDateTime finishedAt,
                                ParagraphAlignment alignment,
                                boolean bold,
                                String startLabel,       // «Допрос начат» / «Жауап алу»
                                String pauseLabel,       // «Прерван (согласно ст.209 УПК РК)»
                                String resumeLabel,      // «Возобновлен (согласно ст.209 УПК РК)»
                                String endLabel,         // «Допрос окончен» / «Аяқталуы»
                                String fallbackStart,    // «__ час. __ мин.» / «__» сағат «__» минутта
                                String fallbackDash) {   // «- час. - мин.» / «__» сағат «__» минутта

        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(alignment);

        boolean hasRealPause = sessions != null && sessions.size() > 1;

        if (sessions != null && !sessions.isEmpty()) {
            InterrogationTimerSessionResponse first = sessions.get(0);
            String startTime = first.getStartedAt() != null
                    ? formatTime(first.getStartedAt()) : fallbackStart;
            addTimeRun(timePara, startLabel + ": " + startTime, bold, true);

            String pausedTime = (hasRealPause && sessions.get(0).getPausedAt() != null)
                    ? formatTime(sessions.get(0).getPausedAt()) : fallbackDash;
            addTimeRun(timePara, pauseLabel + ": " + pausedTime, bold, true);

            String resumedTime = (hasRealPause && sessions.get(1).getStartedAt() != null)
                    ? formatTime(sessions.get(1).getStartedAt()) : fallbackDash;
            addTimeRun(timePara, resumeLabel + ": " + resumedTime, bold, true);

            String end = finishedAt != null ? formatTime(finishedAt) : fallbackStart;
            addTimeRun(timePara, endLabel + ": " + end, bold, false);
        } else {
            addTimeRun(timePara, startLabel + ": " + fallbackStart, bold, true);
            addTimeRun(timePara, pauseLabel + ": " + fallbackDash, bold, true);
            addTimeRun(timePara, resumeLabel + ": " + fallbackDash, bold, true);
            addTimeRun(timePara, endLabel + ": " + fallbackStart, bold, false);
        }
    }

    protected void addTimeRun(XWPFParagraph p, String text, boolean bold, boolean addBreak) {
        XWPFRun r = p.createRun();
        r.setBold(bold);
        r.setFontFamily(FONT);
        r.setFontSize(FONT_SIZE);
        r.setText(text);
        if (addBreak) r.addBreak();
    }

    /** Короткий блок времени (только начало + конец, без перерывов) */
    protected void addTimeBlockSimple(XWPFDocument doc,
                                      List<InterrogationTimerSessionResponse> sessions,
                                      java.time.LocalDateTime finishedAt,
                                      ParagraphAlignment alignment,
                                      String startLabel,
                                      String endLabel,
                                      String fallback) {
        XWPFParagraph timePara = doc.createParagraph();
        timePara.setAlignment(alignment);

        if (sessions != null && !sessions.isEmpty()) {
            InterrogationTimerSessionResponse first = sessions.get(0);
            String startTime = first.getStartedAt() != null ? formatTime(first.getStartedAt()) : fallback;
            addTimeRun(timePara, startLabel + ": " + startTime, false, true);
            String end = finishedAt != null ? formatTime(finishedAt) : fallback;
            addTimeRun(timePara, endLabel + ": " + end, false, false);
        } else {
            addTimeRun(timePara, startLabel + ": " + fallback, false, true);
            addTimeRun(timePara, endLabel + ": " + fallback, false, false);
        }
    }

    protected abstract String formatTime(java.time.LocalDateTime dt);

    // ── Утилиты ──────────────────────────────────────────────────────────────

    protected String safe(String val) {
        return (val != null && !val.isBlank()) ? val : "—";
    }

    protected String formatFio(String fio) {
        if (fio == null || fio.isBlank()) return "—";
        String[] parts = fio.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                sb.append(" ").append(Character.toUpperCase(parts[i].charAt(0))).append(".");
            }
        }
        return sb.toString();
    }

    protected String formatEducations(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getEducations() == null || p.getEducations().isEmpty()) return "—";
        return p.getEducations().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank()
                        ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }
    protected String formatCriminals(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getCriminalRecord() == null || p.getCriminalRecord().isEmpty()) return "—";
        return p.getCriminalRecord().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank()
                        ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    protected String formatMilitaries(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getMilitary() == null || p.getMilitary().isEmpty()) return "—";
        return p.getMilitary().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank()
                        ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    protected String formatRelations(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getRelation() == null || p.getRelation().isEmpty()) return "—";
        return p.getRelation().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank()
                        ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    // ── Низкоуровневые POI-хелперы ───────────────────────────────────────────

    protected void setCellWidth(XWPFTableCell cell, int widthDxa) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth w = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        w.setW(BigInteger.valueOf(widthDxa));
        w.setType(STTblWidth.DXA);
    }

    protected void setCellThinBorder(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders b = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        for (CTBorder border : new CTBorder[]{b.addNewTop(), b.addNewBottom(), b.addNewLeft(), b.addNewRight()}) {
            border.setVal(STBorder.SINGLE);
            border.setSz(BigInteger.valueOf(2));
            border.setColor("000000");
        }
    }

    protected void setCellPadding(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcMar mar = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
        for (CTTblWidth w : new CTTblWidth[]{
                mar.isSetTop() ? mar.getTop() : mar.addNewTop(),
                mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom(),
                mar.isSetLeft() ? mar.getLeft() : mar.addNewLeft(),
                mar.isSetRight() ? mar.getRight() : mar.addNewRight()}) {
            w.setW(BigInteger.valueOf(80));
            w.setType(STTblWidth.DXA);
        }
    }

    protected void removeTableBorders(XWPFTable table) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();
        CTTblBorders borders = tblPr.getTblBorders() != null ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        for (CTBorder b : new CTBorder[]{
                borders.isSetTop() ? borders.getTop() : borders.addNewTop(),
                borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom(),
                borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft(),
                borders.isSetRight() ? borders.getRight() : borders.addNewRight(),
                borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH(),
                borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV()}) {
            b.setVal(STBorder.NONE);
        }
    }
    protected boolean hasMultipleDays(List<InterrogationTimerSessionResponse> sessions, java.time.LocalDateTime finishedAt) {
        if (sessions == null || sessions.isEmpty()) return false;
        java.time.LocalDate firstDay = sessions.get(0).getStartedAt() != null
                ? sessions.get(0).getStartedAt().toLocalDate() : null;
        if (firstDay == null) return false;

        for (InterrogationTimerSessionResponse s : sessions) {
            if (s.getStartedAt() != null && !s.getStartedAt().toLocalDate().equals(firstDay)) return true;
            if (s.getPausedAt() != null && !s.getPausedAt().toLocalDate().equals(firstDay)) return true;
        }
        if (finishedAt != null && !finishedAt.toLocalDate().equals(firstDay)) return true;
        return false;
    }
}