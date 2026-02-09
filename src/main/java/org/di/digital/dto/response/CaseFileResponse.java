package org.di.digital.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseFileResponse {
    private Long id;
    private String originalFileName;
    private String storedFileName;
    private String previewUrl;
    private String downloadUrl;
    private String contentType;
    private Long fileSize;
    private String uploadedAt;
    private String status;
    private boolean isQualification;
}
