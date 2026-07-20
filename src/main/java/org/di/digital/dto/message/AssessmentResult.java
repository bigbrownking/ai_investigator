package org.di.digital.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AssessmentResult {
    private String status;

    @JsonProperty("score_percent")
    private Double scorePercent;

    private String color;
    private String summary;

    @JsonProperty("ruleset_version")
    private String rulesetVersion;
}