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
    private String number;

    @NotBlank
    private String documentType;

    @NotBlank
    private String fio;

    @NotBlank
    private String role;
}