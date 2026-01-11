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
    private String fileUrl;
    private String contentType;
    private Long fileSize;
    private String uploadedAt;
    private String status;
}
