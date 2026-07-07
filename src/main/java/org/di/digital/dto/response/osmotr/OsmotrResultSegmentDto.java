package org.di.digital.dto.response.osmotr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrResultSegmentDto {
    private Long id;
    private String title;
    private Integer startPage;
    private Integer endPage;
    private String inspectionText;
    private Boolean evidenceNeeded;
    private Boolean returnNeeded;
    private String fileUrl;
}
