package org.di.digital.dto.request.plan;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectPlanRequest {

    @NotBlank
    private String comment;
}