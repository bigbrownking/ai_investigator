package org.di.digital.dto.request.interrogation;

import lombok.*;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CleanTranscriptRequest {
    private String text;
    private String language;
}