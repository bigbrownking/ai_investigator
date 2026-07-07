package org.di.digital.dto.response.tree;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreeDataResponse {

    private Long caseId;
    private String caseNumber;
    private List<TreeModuleResponse> modules;
    private LocalDateTime fetchedAt;
}
