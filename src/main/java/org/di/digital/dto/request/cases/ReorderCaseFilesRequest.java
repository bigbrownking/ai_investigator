package org.di.digital.dto.request.cases;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReorderCaseFilesRequest {
    private List<Long> fileIds;
}
