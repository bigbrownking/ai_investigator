package org.di.digital.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditAudioTranscribedTextRequest {
    private Long qaId;
    private String answer;
}
