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
    private String previewUrl;
    private String downloadUrl;
    private String contentType;
    private Long fileSize;
    private String uploadedAt;
    private String completedAt;
    private String status;
    private boolean isQualification;
    private boolean isPlanComponent;
    private boolean isPlan;
    private Integer tom;
    private int pages;
}
