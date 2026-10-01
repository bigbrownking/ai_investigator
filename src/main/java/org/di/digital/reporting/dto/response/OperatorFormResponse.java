package org.di.digital.reporting.dto.response;

import lombok.*;
import org.di.digital.reporting.dto.ColumnDefinitionDto;
import org.di.digital.reporting.model.enums.ReportRowStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * What the regional operator sees: the template's columns and the single row of their region for a date.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperatorFormResponse {
    private String templateCode;
    private String templateName;
    private String templateDescription;
    private Integer templateVersion;
    private List<ColumnDefinitionDto> columns;

    private Long regionId;
    private LocalDate reportDate;
    private ReportRowStatus status;
    /**
     * Saved values of the row (draft or submitted), limited to the template's current columns;
     * empty when nothing was saved.
     */
    private Map<String, Object> cells;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;

    /** The row can still be (re)submitted: only today's report is editable. */
    private boolean editable;
    /** A draft can be saved: editable and not yet submitted (a submitted row is changed by resubmitting). */
    private boolean draftAllowed;
    /** null when nothing was saved yet. */
    private Long lockVersion;
}
