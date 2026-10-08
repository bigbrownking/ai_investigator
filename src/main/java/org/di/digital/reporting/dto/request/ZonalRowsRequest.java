package org.di.digital.reporting.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import org.di.digital.reporting.dto.response.ZonalRowDto;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalRowsRequest {
    private List<ZonalRowDto> rows;
    private Long lockVersion;
    @Size(max = 2000)
    private String comment;
}
