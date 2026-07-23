package org.di.digital.dto.request.qualification;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualificationRephraseRequest {
    private int startSectionId;
    private int startOffset;
    private int endSectionId;
    private int endOffset;
    private String prompt;
}
