package org.di.digital.reporting.validation;

import org.di.digital.reporting.model.ColumnDefinition;
import org.di.digital.reporting.model.SelectOption;
import org.di.digital.reporting.model.enums.ColumnType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class TemplateDefinitionValidator {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    public void validateColumns(List<ColumnDefinition> columns) {
        List<String> errors = new ArrayList<>();
        if (columns == null || columns.isEmpty()) {
            errors.add("Template must have at least one column");
        } else {
            Set<String> columnKeys = new HashSet<>();
            for (ColumnDefinition column : columns) {
                validateColumn(column, columnKeys, errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new ReportingValidationException(errors);
        }
    }

    private void validateColumn(ColumnDefinition column, Set<String> seenKeys, List<String> errors) {
        String key = column.getKey();
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            errors.add("Invalid column key: " + key);
        } else if (!seenKeys.add(key)) {
            errors.add("Duplicate column key: " + key);
        }
        if (column.getLabel() == null || column.getLabel().isBlank()) {
            errors.add("Column " + key + ": label is required");
        }
        if (column.getType() == null) {
            errors.add("Column " + key + ": type is required");
            return;
        }

        List<SelectOption> options = column.getOptions() == null ? List.of() : column.getOptions();
        if (column.getType() != ColumnType.SELECT) {
            if (!options.isEmpty()) {
                errors.add("Column " + key + ": options are allowed only for SELECT columns");
            }
            return;
        }
        if (options.isEmpty()) {
            errors.add("Column " + key + ": SELECT column needs at least one option");
        }
        Set<String> values = new HashSet<>();
        for (SelectOption option : options) {
            if (option.getValue() == null || option.getValue().isBlank()) {
                errors.add("Column " + key + ": option value is required");
            } else if (!values.add(option.getValue())) {
                errors.add("Column " + key + ": duplicate option value " + option.getValue());
            }
            if (option.getLabel() == null || option.getLabel().isBlank()) {
                errors.add("Column " + key + ": option label is required");
            }
        }
    }
}
