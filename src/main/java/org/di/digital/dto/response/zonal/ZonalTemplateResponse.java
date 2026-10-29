package org.di.digital.dto.response.zonal;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private List<ColumnDefinitionDto> columns;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long lockVersion;
}
