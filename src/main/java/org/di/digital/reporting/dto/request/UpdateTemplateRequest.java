package org.di.digital.reporting.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.di.digital.reporting.dto.ColumnDefinitionDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Full replacement of a DRAFT version's content.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTemplateRequest {
    @NotBlank
    private String name;
    private String description;

    @Valid
    @Builder.Default
    private List<ColumnDefinitionDto> columns = new ArrayList<>();

    /** lockVersion from the last read; when set, a concurrent edit results in 409. */
    private Long lockVersion;
}
