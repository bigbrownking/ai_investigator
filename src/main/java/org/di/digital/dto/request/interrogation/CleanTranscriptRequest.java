package org.di.digital.dto.request.interrogation;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanTranscriptRequest {
    private String text;
    private String language;
}