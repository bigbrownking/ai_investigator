package org.di.digital.dto.response.osmotr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.di.digital.model.enums.OsmotrProcessingStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsmotrResultDto {
    private Long id;
    private String sessionId;
    private String caseNumber;
    private String originalFileName;
    private String userEmail;
    private OsmotrProcessingStatus status;
    private String reportTxt;
    private String errorMessage;
    private Long processingDurationSeconds;
    private LocalDateTime createdAt;
    private List<OsmotrResultSegmentDto> segments;
}
