package org.di.digital.reporting.dto.response;

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
