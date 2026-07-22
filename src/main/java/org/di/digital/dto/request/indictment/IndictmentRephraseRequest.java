package org.di.digital.dto.request.indictment;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndictmentRephraseRequest {
    private int startSectionId;
    private int startOffset;
    private int endSectionId;
    private int endOffset;
    private String prompt;
}