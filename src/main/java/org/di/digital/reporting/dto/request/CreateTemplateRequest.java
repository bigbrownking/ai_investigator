package org.di.digital.reporting.dto.request;

import java.util.ArrayList;
import java.util.List;

import org.di.digital.reporting.dto.ColumnDefinitionDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


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
