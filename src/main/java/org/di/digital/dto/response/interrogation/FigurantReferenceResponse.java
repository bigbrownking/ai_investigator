package org.di.digital.dto.response.interrogation;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FigurantReferenceResponse {
    private Long id;
    private String referenceId;
    private String filePath;
}