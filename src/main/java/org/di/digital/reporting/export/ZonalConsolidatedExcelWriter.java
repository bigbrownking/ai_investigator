package org.di.digital.reporting.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.di.digital.reporting.dto.response.ColumnDefinitionDto;
import org.di.digital.reporting.dto.response.SelectOptionDto;
import org.di.digital.reporting.dto.response.ZonalConsolidatedRegion;
import org.di.digital.reporting.dto.response.ZonalConsolidatedResponse;
import org.di.digital.reporting.model.enums.ColumnType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Component
public class ZonalConsolidatedExcelWriter {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final int FIXED_COLUMNS = 3;
    private static final int MAX_EXCEL_DIGITS = 15;
    private static final int MAX_CELL_TEXT = 32_767;
    private static final int MIN_WIDTH_CHARS = 6;
    private static final int MAX_WIDTH_CHARS = 60;

    public byte[] write(ZonalConsolidatedResponse table) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new SheetWriter(workbook, table).write();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build Excel of consolidated zonal template " + table.getTemplateId(), e);
        }
    }

    public static String fileName(ZonalConsolidatedResponse table) {
        String name = table.getTemplateName() == null ? "" : table.getTemplateName()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .trim();
        if (name.isEmpty()) {
            name = "consolidated";
        }
        if (name.length() > 100) {
            name = name.substring(0, 100).trim();
        }
        return name + "_" + table.getAsOf() + ".xlsx";
    }

    private static final class SheetWriter {
        private final ZonalConsolidatedResponse table;
        private final List<ColumnDefinitionDto> columns;
        private final Sheet sheet;
        private final int[] widths;
        private int rowIndex;

        SheetWriter(Workbook workbook, ZonalConsolidatedResponse table) {
            this.table = table;
            this.columns = table.getColumns() == null ? List.of() : table.getColumns();
            this.sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(
                    table.getTemplateName() == null || table.getTemplateName().isBlank() ? "Сводная" : table.getTemplateName()));
            this.widths = new int[FIXED_COLUMNS + columns.size()];
        }

        void write() {
            writeTitle();
            writeHeader();
            List<ZonalConsolidatedRegion> regions = table.getRegions() == null ? List.of() : table.getRegions();
            for (ZonalConsolidatedRegion region : regions) {
                writeRegion(region);
            }
            writeTotals();
            applyWidths();
        }

        private void writeTitle() {
            Row title = sheet.createRow(rowIndex++);
            text(title, 0, table.getTemplateName(), false);
            Row info = sheet.createRow(rowIndex++);
            text(info, 0, "Сводная таблица по регионам на " + format(table.getAsOf(), DATE)
                    + ". Сдали: " + table.getSubmittedRegions() + " из " + table.getTotalRegions()
                    + ". Строк: " + table.getTotalRows()
                    + ". Сформировано: " + format(table.getGeneratedAt(), DATE_TIME), false);
            rowIndex++;
        }

        private void writeHeader() {
            Row header = sheet.createRow(rowIndex++);
            text(header, 0, "№", true);
            text(header, 1, "Регион", true);
            text(header, 2, "Дата отправки", true);
            for (int i = 0; i < columns.size(); i++) {
                text(header, FIXED_COLUMNS + i, columns.get(i).getLabel(), true);
            }
        }

        private void writeRegion(ZonalConsolidatedRegion region) {
            List<Map<String, Object>> rows = region.getRows() == null ? List.of() : region.getRows();
            int count = Math.max(rows.size(), 1);
            for (int r = 0; r < count; r++) {
                Row row = sheet.createRow(rowIndex++);
                writeRegionCells(row, region);
                Map<String, Object> cells = r < rows.size() ? rows.get(r) : Map.of();
                for (int i = 0; i < columns.size(); i++) {
                    writeValue(row, FIXED_COLUMNS + i, columns.get(i), cells.get(columns.get(i).getKey()));
                }
            }
        }

        private void writeRegionCells(Row row, ZonalConsolidatedRegion region) {
            row.createCell(0).setCellValue(region.getPosition());
            measure(0, String.valueOf(region.getPosition()));
            String name = region.getRegionNameRu() != null && !region.getRegionNameRu().isBlank()
                    ? region.getRegionNameRu() : region.getRegionNameKz();
            text(row, 1, name, true);
            text(row, 2, region.getSubmittedAt() == null ? "Не сдано" : format(region.getSubmittedAt(), DATE_TIME), true);
        }

        private void writeValue(Row row, int c, ColumnDefinitionDto column, Object value) {
            if (value == null || (value instanceof String s && s.isBlank())) {
                return;
            }
            ColumnType type = column.getType() == null ? ColumnType.STRING : column.getType();
            switch (type) {
                case NUMBER -> {
                    if (value instanceof BigDecimal number) {
                        number(row, c, number);
                    } else {
                        text(row, c, value.toString(), true);
                    }
                }
                case DATE -> text(row, c, formatDate(value), true);
                case SELECT -> text(row, c, optionLabel(column, value.toString()), true);
                default -> text(row, c, value.toString(), true);
            }
        }

        private void writeTotals() {
            Map<String, BigDecimal> totals = table.getTotals();
            if (totals == null || totals.isEmpty()) {
                return;
            }
            Row row = sheet.createRow(rowIndex++);
            text(row, 1, "Итого", true);
            for (int i = 0; i < columns.size(); i++) {
                BigDecimal total = totals.get(columns.get(i).getKey());
                if (total != null) {
                    number(row, FIXED_COLUMNS + i, total);
                }
            }
        }

        private void number(Row row, int c, BigDecimal number) {
            BigDecimal value = number.stripTrailingZeros();
            String plain = value.toPlainString();
            if (value.precision() > MAX_EXCEL_DIGITS) {
                text(row, c, plain, true);
            } else {
                row.createCell(c).setCellValue(value.doubleValue());
                measure(c, plain);
            }
        }

        private void text(Row row, int c, String value, boolean measured) {
            String text = value == null ? "" : value;
            Cell cell = row.createCell(c);
            cell.setCellValue(text.length() > MAX_CELL_TEXT ? text.substring(0, MAX_CELL_TEXT) : text);
            if (measured) {
                measure(c, text);
            }
        }

        private void measure(int c, String text) {
            int longestLine = 0;
            for (String line : text.split("\n")) {
                longestLine = Math.max(longestLine, line.length());
            }
            widths[c] = Math.max(widths[c], longestLine);
        }

        private void applyWidths() {
            for (int c = 0; c < widths.length; c++) {
                int chars = Math.min(Math.max(widths[c], MIN_WIDTH_CHARS), MAX_WIDTH_CHARS) + 2;
                sheet.setColumnWidth(c, chars * 256);
            }
        }

        private static String optionLabel(ColumnDefinitionDto column, String value) {
            if (column.getOptions() != null) {
                for (SelectOptionDto option : column.getOptions()) {
                    if (value.equals(option.getValue())) {
                        return option.getLabel();
                    }
                }
            }
            return value;
        }

        private static String formatDate(Object value) {
            try {
                return LocalDate.parse(value.toString().trim()).format(DATE);
            } catch (DateTimeParseException e) {
                return value.toString();
            }
        }

        private static String format(LocalDate date, DateTimeFormatter formatter) {
            return date == null ? "" : date.format(formatter);
        }

        private static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
            return dateTime == null ? "" : dateTime.format(formatter);
        }
    }
}