package org.di.digital.reporting.validation;

import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.SelectOption;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates the operator's flat row (columnKey -> value) against the template columns.
 * <ul>
 *   <li>Draft: only the shape is checked (known columns, scalar values, text length); values are kept as entered.</li>
 *   <li>Submit: every value is checked and normalized by column type, required columns must be filled.</li>
 * </ul>
 * Errors are collected per column and reported together.
 */
@Component
public class SubmissionCellsValidator {

    private static final int MAX_TEXT_LENGTH = 2000;
    // Keeps values well inside what jsonb numeric and BigDecimal consumers handle
    private static final int MAX_INTEGER_DIGITS = 18;
    private static final int MAX_FRACTION_DIGITS = 6;

    /** @return cells in template column order, blank values dropped */
    public Map<String, Object> validateDraft(ReportTemplate template, Map<String, Object> cells) {
        Errors errors = new Errors();
        Map<String, Object> input = cells == null ? Map.of() : cells;
        rejectUnknownColumns(template, input, errors);

        Map<String, Object> result = new LinkedHashMap<>();
        for (ColumnDefinition column : template.getColumns()) {
            Object value = input.get(column.getKey());
            if (isBlank(value)) {
                continue;
            }
            if (!isScalar(value)) {
                errors.add(column.getKey(), "a single value is expected, not a list or object");
            } else if (value instanceof String text && text.trim().length() > MAX_TEXT_LENGTH) {
                errors.add(column.getKey(), "text is longer than " + MAX_TEXT_LENGTH + " characters");
            } else {
                result.put(column.getKey(), value instanceof String text ? text.trim() : value);
            }
        }
        errors.throwIfAny();
        return result;
    }

    /**
     * @return normalized cells in template column order: NUMBER -> BigDecimal, STRING -> String,
     *         DATE -> ISO yyyy-MM-dd String, SELECT -> option value
     */
    public Map<String, Object> validateSubmit(ReportTemplate template, Map<String, Object> cells) {
        Errors errors = new Errors();
        Map<String, Object> input = cells == null ? Map.of() : cells;
        rejectUnknownColumns(template, input, errors);

        Map<String, Object> result = new LinkedHashMap<>();
        for (ColumnDefinition column : template.getColumns()) {
            Object value = input.get(column.getKey());
            if (isBlank(value)) {
                if (column.isRequired()) {
                    errors.add(column.getKey(), "value is required");
                }
                continue;
            }
            if (!isScalar(value)) {
                errors.add(column.getKey(), "a single value is expected, not a list or object");
                continue;
            }
            Object normalized = normalize(column, value, errors);
            if (normalized != null) {
                result.put(column.getKey(), normalized);
            }
        }
        errors.throwIfAny();
        return result;
    }

    private Object normalize(ColumnDefinition column, Object value, Errors errors) {
        String key = column.getKey();
        String text = value.toString().trim();
        switch (column.getType()) {
            case NUMBER -> {
                if (value instanceof Boolean) {
                    errors.add(key, "number expected, got " + value);
                    return null;
                }
                BigDecimal number;
                try {
                    number = value instanceof BigDecimal decimal ? decimal : new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    errors.add(key, "number expected, got '" + text + "'");
                    return null;
                }
                // Drop exponent notation (1e3 -> 1000) and bound the size
                number = new BigDecimal(number.stripTrailingZeros().toPlainString());
                if (number.precision() - number.scale() > MAX_INTEGER_DIGITS || number.scale() > MAX_FRACTION_DIGITS) {
                    errors.add(key, "number out of range: at most " + MAX_INTEGER_DIGITS + " digits before and "
                            + MAX_FRACTION_DIGITS + " after the decimal point");
                    return null;
                }
                return number;
            }
            case STRING -> {
                if (text.length() > MAX_TEXT_LENGTH) {
                    errors.add(key, "text is longer than " + MAX_TEXT_LENGTH + " characters");
                    return null;
                }
                return text;
            }
            case DATE -> {
                try {
                    return LocalDate.parse(text).toString();
                } catch (DateTimeParseException e) {
                    errors.add(key, "date in format yyyy-MM-dd expected, got '" + text + "'");
                    return null;
                }
            }
            case SELECT -> {
                Set<String> allowed = column.getOptions() == null ? Set.of() : column.getOptions().stream()
                        .map(SelectOption::getValue)
                        .collect(Collectors.toSet());
                if (!allowed.contains(text)) {
                    errors.add(key, "'" + text + "' is not one of " + allowed);
                    return null;
                }
                return text;
            }
            default -> {
                errors.add(key, "unsupported column type " + column.getType());
                return null;
            }
        }
    }

    private void rejectUnknownColumns(ReportTemplate template, Map<String, Object> input, Errors errors) {
        Set<String> known = template.getColumns().stream()
                .map(ColumnDefinition::getKey)
                .collect(Collectors.toSet());
        input.keySet().stream()
                .filter(key -> !known.contains(key))
                .forEach(key -> errors.add(key, "unknown column"));
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    private boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    /** Collects messages both as a flat list ("key: message") and grouped by column key. */
    private static final class Errors {
        private final List<String> flat = new ArrayList<>();
        private final Map<String, List<String>> byColumn = new LinkedHashMap<>();

        void add(String columnKey, String message) {
            flat.add(columnKey + ": " + message);
            byColumn.computeIfAbsent(columnKey, k -> new ArrayList<>()).add(message);
        }

        void throwIfAny() {
            if (!flat.isEmpty()) {
                throw new ReportingValidationException(flat, byColumn);
            }
        }
    }
}
