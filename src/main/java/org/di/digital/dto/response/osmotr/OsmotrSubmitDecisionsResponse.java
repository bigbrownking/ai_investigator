package org.di.digital.dto.response.osmotr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OsmotrSubmitDecisionsResponse {
    private String status;

    @JsonProperty("session_id")
    private String sessionId;

    private String message;

    @JsonProperty("evidence_download_url")
    private String evidenceDownloadUrl;

    @JsonProperty("return_download_url")
    private String returnDownloadUrl;

    private Map<String, String> downloads;

    private Map<String, String> previews;

    private Map<String, String> files;
}