package org.di.digital.reporting.dto.response;

import lombok.*;
import org.di.digital.reporting.dto.ColumnDefinitionDto;
import org.di.digital.reporting.model.enums.TemplateStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
    private Long id;
    private String code;
    private Integer version;
    private String name;
    private String description;
    private TemplateStatus status;

    private List<ColumnDefinitionDto> columns;

    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime archivedAt;

    private Long lockVersion;
}
