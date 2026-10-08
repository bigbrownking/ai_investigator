package org.di.digital.reporting.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalFormSummaryResponse {
    private Long templateId;
    private String name;
    private String description;
    private int columnCount;
    private int rowCount;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSubmittedAt;
}
