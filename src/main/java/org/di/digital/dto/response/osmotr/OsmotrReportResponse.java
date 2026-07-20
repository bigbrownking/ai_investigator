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

    @JsonProperty("report_filename")
    private String reportFilename;

    @JsonProperty("report_file_base64")
    private String reportFileBase64;

    @JsonProperty("report_txt")
    private String reportTxt;

    private List<OsmotrDataItemDto> results;
    private List<OsmotrDataItemDto> data;
}