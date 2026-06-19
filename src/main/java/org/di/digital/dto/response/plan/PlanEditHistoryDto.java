package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PlanEditHistoryDto {
    private Long id;
    private String editorName;
    private int actionNumber;
    private String fieldKey;
    private String oldValue;
    private String newValue;
    private LocalDateTime editedAt;
}
