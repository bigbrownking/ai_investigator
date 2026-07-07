package org.di.digital.dto.request.interrogation;

import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarkReformulatedRequest {
    private Long qaId;
    private String finalText;
    private String caseNumber;
}
