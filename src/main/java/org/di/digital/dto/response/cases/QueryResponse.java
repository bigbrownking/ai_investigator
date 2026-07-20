package org.di.digital.dto.response.cases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryResponse {
    private String response;
    private List<ReferenceDto> references;
}
