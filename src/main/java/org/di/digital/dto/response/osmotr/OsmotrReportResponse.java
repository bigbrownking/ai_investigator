package org.di.digital.dto.response.osmotr;

import lombok.*;
import java.util.List;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OsmotrReportResponse {
    private String status;
    private String reportFile;
    private String reportTxt;
    private List<OsmotrDataItemDto> data;
}