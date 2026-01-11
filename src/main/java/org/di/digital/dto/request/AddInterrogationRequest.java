package org.di.digital.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddInterrogationRequest {
    @NotBlank
    private String iin;

    @NotBlank
    private String fio;

    @NotBlank
    private String role;
}