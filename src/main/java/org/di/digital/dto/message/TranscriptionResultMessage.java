package org.di.digital.dto.message;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionResultMessage {
    private Long interrogationId;
    private Long qaId;
    private String caseNumber;
    private TranscriptionStatus status;
    private String transcribedText;
    private String errorMessage;
    private String email;
    private LocalDateTime timestamp;
}