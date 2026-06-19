package org.di.digital.dto.response.interrogation;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FigurantResponse {
    private Long id;
    private String externalId;
    private String documentType;
    private String number;
    private String fio;
    private String role;
    private String details;
    private List<FigurantReferenceResponse> references;
}