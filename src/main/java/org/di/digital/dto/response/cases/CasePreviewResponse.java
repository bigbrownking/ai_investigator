package org.di.digital.dto.response.cases;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
public class CasePreviewResponse {
    private Long id;
    private String title;
    private String number;
    private boolean status;
    private String language;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private String ownerEmail;

    public CasePreviewResponse(Long id, String title, String number, boolean status,
                               String language, LocalDateTime createdDate,
                               LocalDateTime updatedDate, String ownerEmail) {
        this.id = id;
        this.title = title;
        this.number = number;
        this.status = status;
        this.language = language;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
        this.ownerEmail = ownerEmail;
    }
}
