package org.di.digital.dto.response.zonal;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalFormResponse {
    private Long templateId;
    private String name;
    private String description;
    private List<ColumnDefinitionDto> columns;

    private Long regionId;
    private List<ZonalRowDto> rows;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSubmittedAt;
    private Long lockVersion;
}
