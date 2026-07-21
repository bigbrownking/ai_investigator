package org.di.digital.dto.response.cases;

import lombok.*;
import org.springframework.data.domain.Page;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CasePageResponse {
    private Page<CaseListResponse> cases;
    private long activeCases;
    private long inactiveCases;
    private long totalDocuments;
    private long totalPages;
    private long totalInterrogations;
    private long audioInterrogations;
}
