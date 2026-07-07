package org.di.digital.dto.request.osmotr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrDecisionDto {
    @JsonProperty("doc_id")
    private String docId;
    private Boolean needed;
}
