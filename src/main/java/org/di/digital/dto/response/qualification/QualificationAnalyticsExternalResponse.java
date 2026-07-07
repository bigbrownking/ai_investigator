package org.di.digital.dto.response.qualification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationAnalyticsExternalResponse {

    @JsonProperty("case_number")
    private String caseNumber;

    @JsonProperty("score_percent")
    private Double scorePercent;

    private String status;

    private String summary;
}