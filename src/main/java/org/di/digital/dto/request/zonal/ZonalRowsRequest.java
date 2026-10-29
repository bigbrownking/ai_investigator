package org.di.digital.dto.request.zonal;

import jakarta.validation.constraints.Size;
import lombok.*;
import org.di.digital.dto.response.zonal.ZonalRowDto;

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
