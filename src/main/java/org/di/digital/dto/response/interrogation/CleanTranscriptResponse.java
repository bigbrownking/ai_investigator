package org.di.digital.dto.response.interrogation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanTranscriptResponse {
    @JsonProperty("corrected_text")
    private String correctedText;
}