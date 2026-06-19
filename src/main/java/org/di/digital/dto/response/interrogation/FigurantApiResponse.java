package org.di.digital.dto.response.interrogation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FigurantApiResponse {

    @JsonProperty("case_id")
    private String caseId;

    private String workspace;

    private List<FigurantDto> figurants;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FigurantDto {
        private String id;
        private String externalId;
        private String name;
        private String type;
        private String role;
        private String iin;
        private String details;
        private Double confidence;
        private List<ReferenceDto> references;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceDto {
        @JsonProperty("reference_id")
        private String referenceId;

        @JsonProperty("file_path")
        private String filePath;
    }
}