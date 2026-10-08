package org.di.digital.reporting.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalConsolidatedResponse {
    private Long templateId;
    private String templateName;
    private LocalDate asOf;
    private List<ColumnDefinitionDto> columns;
    private List<ZonalConsolidatedRegion> regions;
    private int totalRegions;
    private int submittedRegions;
    private int totalRows;
    private Map<String, BigDecimal> totals;
    private LocalDateTime generatedAt;
}
