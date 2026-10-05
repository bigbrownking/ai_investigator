package org.di.digital.reporting.dto.request;

import java.util.ArrayList;
import java.util.List;

import org.di.digital.reporting.dto.ColumnDefinitionDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
