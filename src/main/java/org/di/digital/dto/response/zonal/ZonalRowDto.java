package org.di.digital.dto.response.zonal;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalRowDto {
    private String id;
    @Builder.Default
    private Map<String, Object> cells = new LinkedHashMap<>();
}
