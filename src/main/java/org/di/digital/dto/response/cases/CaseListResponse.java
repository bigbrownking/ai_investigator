package org.di.digital.dto.response.cases;

import lombok.*;
import org.di.digital.model.enums.cases.CaseRejectionReason;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseListResponse implements RejectionEnrichable {
    private Long id;
    private String title;
    private String number;
    private boolean status;
    private boolean hasQualification;
    private boolean hasIndictment;
    private boolean hasPlan;
    private int totalDocuments;
    private int totalPages;
    private int totalInterrogations;
    private long audioInterrogations;
    private LocalDateTime createdDate;
    private LocalDateTime lastActivityDate;
    private String lastActivityType;
    private long priority;
    private String ownerFio;
    private List<String> participantFios;

    private String rejectionReason;
    private String rejectionByFio;
    private LocalDateTime rejectionAt;
}
