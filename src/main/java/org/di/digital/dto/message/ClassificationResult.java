package org.di.digital.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ClassificationResult {
    private String status;

    @JsonProperty("document_type")
    private String documentType;
}