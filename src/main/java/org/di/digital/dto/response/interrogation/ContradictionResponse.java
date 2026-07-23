package org.di.digital.dto.response.interrogation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.di.digital.dto.response.cases.ReferenceDto;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContradictionResponse {

    private List<ContradictionItem> contradictions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContradictionItem {

        private String text;

        @JsonProperty("confidence_percent")
        private Integer confidencePercent;

        private List<ReferenceDto> references;
    }
}