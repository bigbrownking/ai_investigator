package org.di.digital.dto.response.cases;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupedCaseFileResponse {

    private List<TomGroupResponse> toms;

    private int totalPages;

    private int totalFiles;
}