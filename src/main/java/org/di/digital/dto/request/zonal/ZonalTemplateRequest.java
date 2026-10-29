package org.di.digital.dto.request.zonal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalTemplateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;

    @Valid
    @Builder.Default
    private List<ZonalColumnRequest> columns = new ArrayList<>();

    private Long copyFromTemplateId;

    private Boolean active;

    private Long lockVersion;
}
