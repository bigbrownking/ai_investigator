package org.di.digital.dto.response.osmotr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OsmotrSearchResponse {
    private String status;
    @JsonProperty("session_id")
    private String sessionId;
    private String query;
    @JsonProperty("total_matches")
    private Integer totalMatches;
    private List<OsmotrSearchResultItem> results;
}
