package org.di.digital.dto.response.osmotr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OsmotrResolutionResponse {
    private String status;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("case_number")
    private String caseNumber;

    @JsonProperty("total_evidence_docs")
    private Integer totalEvidenceDocs;

    @JsonProperty("resolution_filename")
    private String resolutionFilename;

    @JsonProperty("download_url")
    private String downloadUrl;

    @JsonProperty("resolution_base64")
    private String resolutionBase64;

    @JsonProperty("resolution_preview_txt")
    private String resolutionPreviewTxt;

    @JsonProperty("reasoning_text")
    private String reasoningText;
}
