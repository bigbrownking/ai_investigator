package org.di.digital.reporting.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.di.digital.reporting.dto.response.SelectOptionDto;
import org.di.digital.reporting.model.enums.ColumnType;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalColumnRequest {
    private String key;
    @NotBlank
    @Size(max = 255)
    private String label;
    @NotNull
    private ColumnType type;
    private boolean required;
    @Valid
    @Builder.Default
    private List<SelectOptionDto> options = new ArrayList<>();
}
