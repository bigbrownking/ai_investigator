package org.di.digital.dto.message;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessingMessage implements Serializable {
    private Long caseId;
    private Long caseFileId;
    private String fileUrl;
    private String originalFileName;
    private String userEmail;
    private String caseNumber;
}