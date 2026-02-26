package org.di.digital.dto.message;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioProcessingMessage {
    private Long interrogationId;
    private Long qaId;
    private String caseNumber;
    private String audioFileUrl;
    private String originalFileName;
    private String language;
    private String email;
}