package org.di.digital.dto.response.cases;


import lombok.*;
import org.di.digital.dto.response.interrogation.CaseInterrogationResponse;
import org.di.digital.model.enums.PlanStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseResponse {
    private Long id;
    private String title;
    private String number;
    private boolean status;
    private String language;
    private long totalPages;
    private long totalDocuments;
    private long audioUsed;

    private boolean hasPlan;
    private boolean hasQualification;
    private boolean hasIndictment;

    private List<CaseUserResponse> users;

    private List<CaseFileResponse> files;
    private List<CaseInterrogationResponse> interrogations;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    private LocalDateTime lastActivityDate;
    private String lastActivityType;
    private LocalDateTime qualificationGeneratedAt;
    private LocalDateTime indictmentGeneratedAt;

    private String ownerEmail;
}
