package org.di.digital.reporting.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalTemplateSummaryResponse {
    private Long id;
    private String name;
    private String description;
    private int columnCount;
    /** Shown to zonal users. */
    private boolean active;
    private LocalDateTime updatedAt;
}
