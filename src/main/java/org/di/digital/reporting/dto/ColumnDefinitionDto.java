package org.di.digital.reporting.dto;

import java.util.ArrayList;
import java.util.List;

import org.di.digital.reporting.model.enums.ColumnType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ColumnDefinitionDto {
    @NotBlank
    private String key;
    @NotBlank
    private String label;
    @NotNull
    private ColumnType type;
    private boolean required;
    /** Required for SELECT columns, must be empty for other types. */
    @Valid
    @Builder.Default
    private List<SelectOptionDto> options = new ArrayList<>();
    /** Optional: list position is used when absent. */
    private Integer order;
}
