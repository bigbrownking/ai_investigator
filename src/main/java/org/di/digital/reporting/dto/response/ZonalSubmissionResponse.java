package org.di.digital.reporting.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonalSubmissionResponse {
    private Long id;
    private Long templateId;
    private Long regionId;
    private String regionNameRu;
    private String regionNameKz;
    private String name;
    private List<ColumnDefinitionDto> columns;
    private List<ZonalRowDto> rows;
    private int rowCount;
    private LocalDate reportDate;
    private LocalDateTime submittedAt;
    private Long submittedBy;
    private String comment;
    private Long formLockVersion;
}
