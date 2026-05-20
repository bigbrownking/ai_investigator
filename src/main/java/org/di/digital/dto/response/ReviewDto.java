package org.di.digital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewDto {
    private Long id;
    private String subject;
    private String message;
    private LocalDateTime createdAt;

    private String originalFileName;
    private String contentType;
    private String previewUrl;
    private String downloadUrl;
}
