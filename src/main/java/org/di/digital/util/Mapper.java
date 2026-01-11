package org.di.digital.util;

import org.di.digital.dto.response.CaseFileResponse;
import org.di.digital.dto.response.CaseInterrogationResponse;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.model.Case;
import org.di.digital.model.CaseInterrogation;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class Mapper {
    public CaseInterrogationResponse mapToInterrogationResponse(CaseInterrogation interrogation) {
        return CaseInterrogationResponse.builder()
                .id(interrogation.getId())
                .iin(interrogation.getIin())
                .fio(interrogation.getFio())
                .role(interrogation.getRole())
                .date(String.valueOf(interrogation.getDate()))
                .status(interrogation.getStatus().name())
                .build();
    }

    public CaseResponse mapToCaseResponse(Case caseEntity) {
        return CaseResponse.builder()
                .id(caseEntity.getId())
                .title(caseEntity.getTitle())
                .number(caseEntity.getNumber())
                .description(caseEntity.getDescription())
                .files(caseEntity.getFiles().stream()
                        .map(f -> CaseFileResponse.builder()
                                .id(f.getId())
                                .originalFileName(f.getOriginalFileName())
                                .contentType(f.getContentType())
                                .fileSize(f.getFileSize())
                                .status(f.getStatus().getLabel())
                                .uploadedAt(String.valueOf(f.getUploadedAt()))
                                .build())
                        .collect(Collectors.toList()))
                .interrogations(caseEntity.getInterrogations().stream()
                        .map(f -> CaseInterrogationResponse.builder()
                                .id(f.getId())
                                .iin(f.getIin())
                                .fio(f.getFio())
                                .role(f.getRole())
                                .date(String.valueOf(f.getDate()))
                                .status(f.getStatus().getLabel())
                                .build())
                        .collect(Collectors.toList()))
                .createdDate(caseEntity.getCreatedDate())
                .updatedDate(caseEntity.getUpdatedDate())
                .build();
    }
}
