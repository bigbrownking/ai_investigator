package org.di.digital.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectOptionDto {
    @NotBlank
    private String value;
    @NotBlank
    private String label;
}
