package org.di.digital.reporting.dto.response;

import lombok.*;
import org.di.digital.reporting.model.enums.ReportRowStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One region's row in the consolidated table.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedRowResponse {
    /** 1-based row number in the fixed region order. */
    private int position;
    private Long regionId;
    private String regionNameRu;
    private String regionNameKz;
    private ReportRowStatus status;
    /** Every template column is present; values are null unless the region has SUBMITTED. */
    private Map<String, Object> cells;
    private LocalDateTime submittedAt;
}
