package org.di.digital.service.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PlanDocumentFormatter extends BaseDocumentFormatter {
    private static final long LANDSCAPE_TAB = 15100L;

    public byte[] generate(Map<String, Object> plan, String approvedBy) throws IOException {

        try (XWPFDocument doc = new XWPFDocument()) {

            setPageMargins(doc);
            setLandscape(doc);

            Map<String, Object> titleInfo =
                    (Map<String, Object>) plan.get("plan_title_info");

            Map<String, Object> caseData =
                    (Map<String, Object>) plan.get("case_data");

            List<Map<String, Object>> actions =
                    (List<Map<String, Object>>) plan.get("actions");

            // ─────────────────────────────────────
            // УТВЕРЖДАЮ
            // ─────────────────────────────────────

            addApprovalBlock(doc, titleInfo, approvedBy);

            addEmptyLine(doc);

            // ─────────────────────────────────────
            // ПЛАН
            // ─────────────────────────────────────

            formatTitle(doc.createParagraph(), "ПЛАН");

            String header =
                    "по уголовному делу №"
                            + titleInfo.get("номер_дела")
                            + " по "
                            + titleInfo.get("статья_ук_рк") + "УК РК";

            formatSubtitle(doc.createParagraph(), header);

            String city = String.valueOf(titleInfo.get("город"));
            String date = String.valueOf(titleInfo.get("год"));

            formatCityDateLine(doc, city, "«__» __________ 20__ г.");

            addEmptyLine(doc);

            // ─────────────────────────────────────
            // Таблица
            // ─────────────────────────────────────

            XWPFTable table = doc.createTable(actions.size() + 1, 5);

            setTableBorders(table);
            setTableCellMargins(table);
            table.setWidth("100%");

            createHeader(table.getRow(0));

            for (int i = 0; i < actions.size(); i++) {

                Map<String, Object> action = actions.get(i);

                XWPFTableRow row = table.getRow(i + 1);

                fillCell(row.getCell(0),
                        String.valueOf(action.get("номер")));

                fillCell(row.getCell(1),
                        String.valueOf(action.get("действие")));

                fillCell(row.getCell(2),
                        String.valueOf(action.get("цель")));

                fillCell(row.getCell(3),
                        String.valueOf(action.get("исполнитель")));

                fillCell(row.getCell(4),
                        "Исполнить до\n" + action.get("срок"));
            }

            addEmptyLine(doc);

            // ─────────────────────────────────────
            // Заключение
            // ─────────────────────────────────────

            formatRegularParagraph(
                    doc.createParagraph(),
                    "Данные следственные действия не являются "
                            + "исчерпывающими, по мере необходимости "
                            + "провести и другие следственно-оперативные "
                            + "мероприятия с составлением дополнительного плана."
            );

            addEmptyLine(doc);
            addEmptyLine(doc);

            // ─────────────────────────────────────
            // Подписи
            // ─────────────────────────────────────

            addSignature(doc,
                    "Следователь",
                    String.valueOf(caseData.get("фио_следователя")));

            addEmptyLine(doc);

            addSignature(doc,
                    "Согласовано\nРуководитель СУ",
                    "");

            return writeDocument(doc);
        }
    }

    // ─────────────────────────────────────────────

    private void addApprovalBlock(XWPFDocument doc, Map<String, Object> titleInfo, String approvedBy) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        setLineSpacing(p);

        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontFamily(FONT);
        r.setFontSize(FONT_SIZE);

        String year = titleInfo.get("год") != null
                ? (String) titleInfo.get("год")
                : "«__» _____________ 20__ г";

        r.setText("«УТВЕРЖДАЮ»");
        r.addBreak();
        r.setText("Заместитель руководителя ДЭР " + titleInfo.get("регион_дэр"));
        r.addBreak();
        r.setText("____________________________");
        r.addBreak();

        if (approvedBy != null && !approvedBy.isBlank()) {
            r.setText(approvedBy);
        } else {
            r.setText("____________________________");
        }

        r.addBreak();
        r.setText(year);
    }

    private void createHeader(XWPFTableRow row) {

        setHeaderCell(row.getCell(0), "№");
        setHeaderCell(row.getCell(1), "Следственные действия");
        setHeaderCell(row.getCell(2), "Цель");
        setHeaderCell(row.getCell(3), "Исполнитель");
        setHeaderCell(row.getCell(4), "Срок");
    }

    private void setHeaderCell(XWPFTableCell cell, String text) {

        cell.removeParagraph(0);

        XWPFParagraph p = cell.addParagraph();

        p.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun r = p.createRun();

        r.setBold(true);
        r.setFontFamily(FONT);
        r.setFontSize(FONT_SIZE);

        r.setText(text);
    }

    private void fillCell(XWPFTableCell cell, String text) {

        cell.removeParagraph(0);

        XWPFParagraph p = cell.addParagraph();

        p.setAlignment(ParagraphAlignment.BOTH);

        setLineSpacing(p);

        XWPFRun r = p.createRun();

        r.setFontFamily(FONT);
        r.setFontSize(12);

        r.setText(text);
    }

    private void addSignature(XWPFDocument doc,
                              String left,
                              String right) {

        XWPFParagraph p = doc.createParagraph();

        p.setAlignment(ParagraphAlignment.LEFT);

        setLineSpacing(p);

        XWPFRun r = p.createRun();

        r.setFontFamily(FONT);
        r.setFontSize(FONT_SIZE);

        r.setText(left);
        r.addBreak();

        r.setText("____________________________");

        if (!right.isBlank()) {
            r.addBreak();
            r.setText(right);
        }
    }

    private void setTableBorders(XWPFTable table) {

        CTTblPr tblPr = table.getCTTbl().getTblPr();

        CTTblBorders borders = tblPr.addNewTblBorders();

        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);
    }
    protected void setLandscape(XWPFDocument doc) {

        CTSectPr sectPr = doc.getDocument()
                .getBody()
                .isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();

        CTPageSz pageSize = sectPr.isSetPgSz()
                ? sectPr.getPgSz()
                : sectPr.addNewPgSz();

        // A4 landscape
        pageSize.setOrient(STPageOrientation.LANDSCAPE);

        // width/height в twips
        pageSize.setW(java.math.BigInteger.valueOf(16840));
        pageSize.setH(java.math.BigInteger.valueOf(11900));
    }

    private void setTableCellMargins(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        CTTblCellMar cellMar = tblPr.isSetTblCellMar()
                ? tblPr.getTblCellMar()
                : tblPr.addNewTblCellMar();

        CTTblWidth left  = cellMar.isSetLeft()   ? cellMar.getLeft()   : cellMar.addNewLeft();
        CTTblWidth right = cellMar.isSetRight()  ? cellMar.getRight()  : cellMar.addNewRight();
        CTTblWidth top   = cellMar.isSetTop()    ? cellMar.getTop()    : cellMar.addNewTop();
        CTTblWidth bottom= cellMar.isSetBottom() ? cellMar.getBottom() : cellMar.addNewBottom();

        left  .setW(BigInteger.valueOf(100)); left  .setType(STTblWidth.DXA);
        right .setW(BigInteger.valueOf(100)); right .setType(STTblWidth.DXA);
        top   .setW(BigInteger.valueOf(60));  top   .setType(STTblWidth.DXA);
        bottom.setW(BigInteger.valueOf(60));  bottom.setType(STTblWidth.DXA);
    }
    @Override
    protected void formatCityDateLine(XWPFDocument doc, String city, String date) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        setLineSpacing(para);

        // Правый таб на всю ширину ландшафтной страницы
        CTPPr pPr = para.getCTP().isSetPPr()
                ? para.getCTP().getPPr()
                : para.getCTP().addNewPPr();
        CTTabs tabs = pPr.isSetTabs() ? pPr.getTabs() : pPr.addNewTabs();
        CTTabStop tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        tab.setPos(BigInteger.valueOf(LANDSCAPE_TAB));

        XWPFRun cityRun = para.createRun();
        cityRun.setBold(true);
        cityRun.setText(city);
        cityRun.setFontFamily(FONT);
        cityRun.setFontSize(FONT_SIZE);
        cityRun.addTab();

        XWPFRun dateRun = para.createRun();
        dateRun.setBold(true);
        dateRun.setText(date);
        dateRun.setFontFamily(FONT);
        dateRun.setFontSize(FONT_SIZE);
    }
}