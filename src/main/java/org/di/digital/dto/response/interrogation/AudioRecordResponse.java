package org.di.digital.dto.response.interrogation;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AudioRecordResponse {
    private Long id;
    private String audioUrl;
    private String transcribedText;
    private String status;
    private LocalDateTime createdAt;
}
