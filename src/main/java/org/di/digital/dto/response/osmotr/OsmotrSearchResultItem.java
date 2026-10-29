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
public class OsmotrSearchResultItem {
    @JsonProperty("doc_id")
    private String docId;
    private String title;
    @JsonProperty("start_page")
    private Integer startPage;
    @JsonProperty("end_page")
    private Integer endPage;
    @JsonProperty("match_type")
    private String matchType;
    private Double score;
    private String snippet;
}
