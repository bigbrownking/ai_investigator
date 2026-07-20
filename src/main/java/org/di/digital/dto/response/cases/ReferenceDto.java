package org.di.digital.dto.response.cases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceDto {
    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("file_path")
    private String filePath;

    private String opis;

    private String content;

    @JsonProperty("legal_citations")
    private Object legalCitations;
}