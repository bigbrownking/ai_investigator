package org.di.digital.dto.response.zonal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectOptionDto {
    @Size(max = 64)
    private String value;

    @NotBlank
    @Size(max = 255)
    private String label;
}
