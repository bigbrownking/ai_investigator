package org.di.digital.reporting.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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
