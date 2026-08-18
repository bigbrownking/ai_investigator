package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.osmotr.OsmotrResultDto;
import org.di.digital.dto.response.osmotr.OsmotrResultSegmentDto;
import org.di.digital.model.osmotr.OsmotrResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OsmotrMapper {
    public OsmotrResultDto toDto(OsmotrResult result) {
        return OsmotrResultDto.builder()
                .id(result.getId())
                .sessionId(result.getSessionId())
                .caseNumber(result.getCaseNumber())
                .originalFileName(result.getOriginalFileName())
                .userEmail(result.getUserEmail())
                .status(result.getStatus())
                .reportTxt(result.getReportTxt())
                .errorMessage(result.getErrorMessage())
                .processingDurationSeconds(result.getProcessingDurationSeconds())
                .createdAt(result.getCreatedAt())
                .segments(result.getSegments().stream()
                        .map(s -> OsmotrResultSegmentDto.builder()
                                .id(s.getId())
                                .title(s.getTitle())
                                .startPage(s.getStartPage())
                                .endPage(s.getEndPage())
                                .inspectionText(s.getInspectionText())
                                .evidenceNeeded(s.getEvidenceNeeded())
                                .returnNeeded(s.getReturnNeeded())
                                .fileUrl(s.getFileUrl())
                                .build())
                        .toList())
                .build();
    }
}
