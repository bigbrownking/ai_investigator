package org.di.digital.dto.response.support;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupportTicketPhotoDto {
    private Long id;
    private String originalFileName;
    private String contentType;
    private String previewUrl;
    private String downloadUrl;
}
