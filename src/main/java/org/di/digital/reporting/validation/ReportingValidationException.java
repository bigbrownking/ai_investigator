package org.di.digital.reporting.validation;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
