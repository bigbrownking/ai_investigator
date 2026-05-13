package org.di.digital.dto.response;

import lombok.*;
import org.springframework.data.domain.Page;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CasePageResponse {
    private Page<CaseListResponse> cases;
    private long totalDocuments;
    private long totalPages;
    private long totalInterrogations;
    private long audioInterrogations;
}
