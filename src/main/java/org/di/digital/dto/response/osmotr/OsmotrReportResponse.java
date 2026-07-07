package org.di.digital.dto.response.osmotr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrReportResponse {
    private String status;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("report_file")
    private String reportFile;

    @JsonProperty("report_txt")
    private String reportTxt;

    private List<OsmotrDataItemDto> results;
    private List<OsmotrDataItemDto> data;
}