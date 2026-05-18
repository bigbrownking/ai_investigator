package org.di.digital.dto.response;

import lombok.*;
import org.di.digital.model.enums.TreeModuleType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreeModuleResponse {

    private Long id;
    private TreeModuleType moduleType;
    private String moduleName;
    private String jsonData;
    private Integer version;
    private LocalDateTime fetchedAt;
    private Boolean fetchSuccess;
    private String errorMessage;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
