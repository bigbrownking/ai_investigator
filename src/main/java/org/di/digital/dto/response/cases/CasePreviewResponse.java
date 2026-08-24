package org.di.digital.dto.response.cases;

import lombok.*;
import org.di.digital.model.enums.cases.CaseRejectionReason;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CasePreviewResponse implements RejectionEnrichable {
    private Long id;
    private String title;
    private String number;
    private boolean status;
    private String language;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private String ownerFio;

    private String rejectionReason;
    private String rejectionByFio;
    private LocalDateTime rejectionAt;

    public CasePreviewResponse(Long id, String title, String number, boolean status,
                               String language, LocalDateTime createdDate,
                               LocalDateTime updatedDate, String ownerFio) {
        this.id = id;
        this.title = title;
        this.number = number;
        this.status = status;
        this.language = language;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
        this.ownerFio = ownerFio;
    }
}
