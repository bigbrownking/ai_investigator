package org.di.digital.dto.response.cases;

import lombok.*;
import org.di.digital.dto.response.cases.CaseFileResponse;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TomGroupResponse {

    private Integer tom;

    private int totalPages;

    private int totalFiles;

    private List<CaseFileResponse> files;
}