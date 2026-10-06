package org.di.digital.reporting.validation;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answered with 422 by ReportingExceptionHandler; extends IllegalStateException so that outside the
 * reporting controllers GlobalExceptionHandler still maps it to 422.
 * {@code errors} is the flat list of all messages; {@code cellErrors} groups the ones that belong to
 * a column (columnKey -> messages) so a client can highlight the offending cells.
 */
@Getter
public class ReportingValidationException extends IllegalStateException {
    private final List<String> errors;
    private final Map<String, List<String>> cellErrors;

    public ReportingValidationException(List<String> errors) {
        this(errors, Map.of());
    }

    public ReportingValidationException(List<String> errors, Map<String, List<String>> cellErrors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
        this.cellErrors = new LinkedHashMap<>(cellErrors);
    }
}
