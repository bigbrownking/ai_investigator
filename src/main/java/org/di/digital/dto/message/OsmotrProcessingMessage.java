package org.di.digital.dto.message;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrProcessingMessage {
    private Long fileId;
    private String caseNumber;
    private String originalFileName;
    private String fileUrl;
    private String userEmail;
    private Long userId;
}