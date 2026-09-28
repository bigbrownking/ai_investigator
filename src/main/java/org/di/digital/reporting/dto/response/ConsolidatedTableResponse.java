package org.di.digital.reporting.dto.response;

import lombok.*;
import org.di.digital.reporting.dto.ColumnDefinitionDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Main admin's consolidated table for one form and one date: one row per region in fixed order.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedTableResponse {
    private String templateCode;
    private String templateName;
    private Integer templateVersion;
    private LocalDate reportDate;
    private List<ColumnDefinitionDto> columns;
    private List<ConsolidatedRowResponse> rows;

    /** Region counts by row status; submitted + draft + notSubmitted = total. */
    private int totalRegions;
    private int submittedRegions;
    private int draftRegions;
    private int notSubmittedRegions;
    /** Built on request from the latest data; "Refresh" is simply a repeated GET. */
    private LocalDateTime generatedAt;
}
