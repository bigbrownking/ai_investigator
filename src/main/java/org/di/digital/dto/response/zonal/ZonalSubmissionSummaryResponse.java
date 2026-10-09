package org.di.digital.dto.response.zonal;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalSubmissionSummaryResponse {
    private Long id;
    private Long templateId;
    private Long regionId;
    private String regionNameRu;
    private String regionNameKz;
    private String name;
    private int rowCount;
    private LocalDate reportDate;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String comment;
}
