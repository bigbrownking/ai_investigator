package org.di.digital.model.zonal;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableRow {
    private String id;
    @Builder.Default
    private Map<String, Object> cells = new LinkedHashMap<>();
}
