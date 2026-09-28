package org.di.digital.reporting.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.di.digital.reporting.dto.ColumnDefinitionDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a new report form (version 1, DRAFT). Also the format of entries in template-seed.json.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequest {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,63}$",
            message = "must start with a letter and contain only latin letters, digits and '_'")
    private String code;
    @NotBlank
    private String name;
    private String description;

    @Valid
    @Builder.Default
    private List<ColumnDefinitionDto> columns = new ArrayList<>();
}
