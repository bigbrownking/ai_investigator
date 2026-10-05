package org.di.digital.reporting.dto.request;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The operator's single row for a date: used by both draft and submit.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRowRequest {
    /** yyyy-MM-dd */
    @NotNull
    private LocalDate reportDate;

    /** Flat row: columnKey -> value. */
    @Builder.Default
    private Map<String, Object> cells = new HashMap<>();

    /** lockVersion from the last read; when set, a concurrent edit results in 409. */
    private Long lockVersion;
}
