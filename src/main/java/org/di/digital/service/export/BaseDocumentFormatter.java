package org.di.digital.service.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class BaseDocumentFormatter {

    protected static final String FONT = "Times New Roman";
    protected static final int FONT_SIZE = 14;
    protected static final long TAB_RIGHT_POS = 9360L;

    // ─── Page setup ───────────────────────────────────────────────────────────

    protected void setPageMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(567));
        pageMar.setBottom(BigInteger.valueOf(567));
        pageMar.setLeft(BigInteger.valueOf(1134));
        pageMar.setRight(BigInteger.valueOf(567));
    }

    protected void setLineSpacing(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setLineRule(STLineSpacingRule.AUTO);
        spacing.setLine(BigInteger.valueOf(240));
        spacing.setBefore(BigInteger.valueOf(0));
        spacing.setAfter(BigInteger.valueOf(0));
        if (!pPr.isSetContextualSpacing()) pPr.addNewContextualSpacing();
    }

    // ─── Formatters ───────────────────────────────────────────────────────────

    protected void formatTitle(XWPFParagraph p, String text) {
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setIndentationFirstLine(0);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text); r.setBold(true);
        r.setFontSize(16); r.setFontFamily(FONT);
    }

    protected void formatSubtitle(XWPFParagraph p, String text) {
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setIndentationFirstLine(0);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text); r.setFontSize(FONT_SIZE); r.setFontFamily(FONT);
    }

    protected void formatSectionHeader(XWPFParagraph p, String text) {
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setIndentationFirstLine(0);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text); r.setBold(true);
        r.setFontSize(FONT_SIZE); r.setFontFamily(FONT);
    }

    protected void formatRegularParagraph(XWPFParagraph p, String text) {
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(convertPtToTwips(36));
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text); r.setFontSize(FONT_SIZE); r.setFontFamily(FONT);
    }

    protected void formatSignatureLine(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        p.setIndentationFirstLine(0);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text); r.setFontFamily(FONT); r.setFontSize(FONT_SIZE);
    }

    protected void formatSignatureLineWithTabTwoParts(XWPFDocument doc, String left, String right) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setIndentationFirstLine(0);
        setLineSpacing(para);
        addRightTabStop(para);

        XWPFRun leftRun = para.createRun();
        leftRun.setText(left); leftRun.setFontFamily(FONT); leftRun.setFontSize(FONT_SIZE);
        leftRun.addTab();

        XWPFRun rightRun = para.createRun();
        rightRun.setText(right); rightRun.setFontFamily(FONT); rightRun.setFontSize(FONT_SIZE);
    }

    protected void formatSubtitleBold(XWPFParagraph p, String text) {
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setIndentationFirstLine(0);
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(FONT_SIZE);
        r.setFontFamily(FONT);
    }
    protected void formatListHeader(XWPFParagraph p, String text) {
        p.setAlignment(ParagraphAlignment.LEFT);
        p.setIndentationFirstLine(convertPtToTwips(32));
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(FONT_SIZE);
        r.setFontFamily(FONT);
    }
    protected void formatSubjectParagraph(XWPFParagraph p, String text) {
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setIndentationFirstLine(0);
        p.setIndentationLeft(convertPtToTwips(240));
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(FONT_SIZE);
        r.setFontFamily(FONT);
    }
    protected int handleHeaderDateLine(String[] paragraphs, int currentIndex,
                                       XWPFDocument doc, String left) {
        for (int j = currentIndex + 1; j < paragraphs.length; j++) {
            String next = stripStars(paragraphs[j].trim());
            if (next.isEmpty()) continue;
            if (isDate(next)) {
                formatCityDateLine(doc, left, next);
                return j;
            }
            break;
        }
        formatRegularParagraph(doc.createParagraph(), left);
        return currentIndex;
    }
    protected void formatCityDateLine(XWPFDocument doc, String city, String date) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setSpacingBefore(0); para.setSpacingAfter(0);
        setLineSpacing(para);
        addRightTabStop(para);

        XWPFRun cityRun = para.createRun();
        cityRun.setBold(true); cityRun.setText(city);
        cityRun.setFontFamily(FONT); cityRun.setFontSize(FONT_SIZE);
        cityRun.addTab();

        XWPFRun dateRun = para.createRun();
        dateRun.setBold(true); dateRun.setText(date);
        dateRun.setFontFamily(FONT); dateRun.setFontSize(FONT_SIZE);
    }

    protected void addEmptyLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        setLineSpacing(p);
        XWPFRun r = p.createRun();
        r.setFontSize(FONT_SIZE); r.setFontFamily(FONT); r.setText("");
    }

    // ─── Detectors ────────────────────────────────────────────────────────────

    protected String stripStars(String text) {
        return text.replace("*", "").trim();
    }

    protected boolean isCity(String text) {
        return text.startsWith("Составлен")
                || text.startsWith("г.")
                || text.startsWith("г ")
                || text.startsWith("город ");
    }

    protected boolean isDate(String text) {
        return text.contains("года") && (
                text.matches(".*\\d{1,2}\\s+[\\p{L}\\p{M}]+\\s+\\d{4}.*") ||
                        (text.contains("«") && text.contains("»"))
        );
    }

    protected boolean isInvestigatorSignatureBlock(String text, String[] paragraphs, int currentIndex) {
        if (!(text.startsWith("Следователь") || text.startsWith("Дознаватель") || text.startsWith("Старший следователь")))
            return false;
        if (text.contains("рассмотрев") || text.contains("УСТАНОВИЛ") || text.contains("допросил"))
            return false;
        int nonEmptyAhead = 0;
        for (int j = currentIndex + 1; j < paragraphs.length; j++) {
            if (!stripStars(paragraphs[j].trim()).isEmpty()) nonEmptyAhead++;
        }
        return nonEmptyAhead <= 5;
    }

    // ─── Handlers ─────────────────────────────────────────────────────────────

    protected int handleCityDateLine(String[] paragraphs, int currentIndex,
                                     XWPFDocument doc, String city) {
        for (int j = currentIndex + 1; j < paragraphs.length; j++) {
            String next = stripStars(paragraphs[j].trim());
            if (next.isEmpty()) continue;
            if (isDate(next)) {
                formatCityDateLine(doc, city, next);
                return j;
            }
            break;
        }
        XWPFParagraph p = doc.createParagraph();
        formatRegularParagraph(p, city);
        return currentIndex;
    }

    protected int handleInvestigatorSignatureBlock(String[] paragraphs, int currentIndex,
                                                 XWPFDocument doc) {
        List<String> lines = new ArrayList<>();
        int lastIndex = currentIndex;

        for (int j = currentIndex; j < paragraphs.length; j++) {
            String line = stripStars(paragraphs[j].trim());
            if (line.isEmpty()) continue;
            lines.addAll(splitPositionLine(line));
            lastIndex = j;
        }

        if (lines.isEmpty()) return lastIndex;

        // Последняя строка — всегда ФИО, предпоследняя — с ней объединяется
        if (lines.size() >= 2) {
            String fioLine = lines.get(lines.size() - 1);
            String regionLine = lines.get(lines.size() - 2);

            for (int i = 0; i < lines.size() - 2; i++) {
                formatSignatureLine(doc, lines.get(i));
            }

            formatSignatureLineWithTabTwoParts(doc, regionLine, fioLine);
        } else {
            // Только одна строка — выводим как есть
            formatSignatureLine(doc, lines.get(0));
        }

        return lastIndex;
    }

    protected void handleTrailingHeader(XWPFDocument doc, String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.matches()) {
            String body = m.group(1).trim();
            if (body.endsWith(",")) body = body.substring(0, body.length() - 1).trim();
            String header = m.group(2).trim();
            if (!body.isEmpty()) formatRegularParagraph(doc.createParagraph(), body);
            addEmptyLine(doc);
            formatSectionHeader(doc.createParagraph(), header);
            addEmptyLine(doc);
        } else {
            formatRegularParagraph(doc.createParagraph(), text);
        }
    }

    protected List<String> splitPositionLine(String line) {
        String[] splitPoints = {
                "Следственного", "Департамента", "Агентства", "Управления", "отдела", "отдел", "по "
        };
        boolean startsWithPosition = line.startsWith("Следователь") || line.startsWith("Дознаватель")
                || line.startsWith("Старший следователь") || line.startsWith("Заместитель руководителя")
                || line.startsWith("Руководитель");
        if (!startsWithPosition) return List.of(line);

        List<String> result = new ArrayList<>();
        String remaining = line;
        for (String sp : splitPoints) {
            int idx = remaining.indexOf(" " + sp);
            if (idx > 0) {
                result.add(remaining.substring(0, idx).trim());
                remaining = remaining.substring(idx).trim();
            }
        }
        if (!remaining.isEmpty()) result.add(remaining.trim());
        return result.isEmpty() ? List.of(line) : result;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void addRightTabStop(XWPFParagraph para) {
        CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTTabs tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        CTTabStop tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        tab.setPos(BigInteger.valueOf(TAB_RIGHT_POS));
    }

    private void writeTabLine(XWPFParagraph para, String left, String right) {
        XWPFRun l = para.createRun();
        l.setText(left); l.setFontFamily(FONT); l.setFontSize(FONT_SIZE);
        l.addTab();
        XWPFRun r = para.createRun();
        r.setText(right); r.setFontFamily(FONT); r.setFontSize(FONT_SIZE);
    }

    protected byte[] writeDocument(XWPFDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }

    protected int convertPtToTwips(double pt) {
        return (int) (pt * 20);
    }
}