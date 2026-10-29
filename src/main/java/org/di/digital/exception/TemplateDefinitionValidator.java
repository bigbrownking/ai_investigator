package org.di.digital.exception;

import org.di.digital.model.zonal.ColumnDefinition;
import org.di.digital.model.zonal.SelectOption;
import org.di.digital.model.enums.zonal.ColumnType;
import org.springframework.stereotype.Component;

import java.util.*;
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
            Set<String> columnLabels = new HashSet<>();
            for (ColumnDefinition column : columns) {
                validateColumn(column, columnKeys, columnLabels, errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new ReportingValidationException(errors);
        }
    }

    private void validateColumn(ColumnDefinition column, Set<String> seenKeys,
                                Set<String> seenLabels, List<String> errors) {
        String key = column.getKey();
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            errors.add("Invalid column key: " + key);
        } else if (!seenKeys.add(key)) {
            errors.add("Duplicate column key: " + key);
        }

        String label = column.getLabel();
        if (label == null || label.isBlank()) {
            errors.add("Column " + key + ": label is required");
        } else if (!seenLabels.add(normalize(label))) {
            errors.add("Duplicate column name: " + label.trim());
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
            errors.add("Column '" + label + "': SELECT column needs at least one option");
        }

        Set<String> values = new HashSet<>();
        Set<String> optionLabels = new HashSet<>();
        for (SelectOption option : options) {
            if (option == null) {
                errors.add("Column '" + label + "': empty option");
                continue;
            }
            String value = option.getValue();
            if (value == null || !KEY_PATTERN.matcher(value).matches()) {
                errors.add("Column '" + label + "': invalid option value " + value);
            } else if (!values.add(value)) {
                errors.add("Column '" + label + "': duplicate option value " + value);
            }

            String optionLabel = option.getLabel();
            if (optionLabel == null || optionLabel.isBlank()) {
                errors.add("Column '" + label + "': option label is required");
            } else if (!optionLabels.add(normalize(optionLabel))) {
                errors.add("Column '" + label + "': duplicate option '" + optionLabel.trim() + "'");
            }
        }
    }

    private static String normalize(String s) {
        return s.trim()
                .replaceAll("\\s+", " ")
                .replace('ё', 'е').replace('Ё', 'Е')
                .toLowerCase(Locale.ROOT);
    }
}
