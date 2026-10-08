package org.di.digital.reporting.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalConsolidatedRegion {
    private int position;
    private Long regionId;
    private String regionNameRu;
    private String regionNameKz;
    private Long submissionId;
    private LocalDateTime submittedAt;
    private List<Map<String, Object>> rows;
}
