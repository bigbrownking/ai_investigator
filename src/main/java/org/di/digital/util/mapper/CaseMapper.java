package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.cases.*;
import org.di.digital.dto.response.interrogation.FigurantReferenceResponse;
import org.di.digital.dto.response.interrogation.FigurantResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.cases.CaseMemberHistory;
import org.di.digital.model.cases.RejectionReasonStatus;
import org.di.digital.model.interrogation.CaseFigurant;
import org.di.digital.model.user.User;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CaseMapper {

    private final FileUrlResolver fileUrls;
    private final TaskQueueService taskQueueService;
    private final InterrogationMapper interrogationMapper;

    public CaseResponse toResponse(Case c) {
        return CaseResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .number(c.getNumber())
                .status(c.isStatus())
                .language(c.getLanguage())
                .totalDocuments(c.getFiles().size())
                .audioUsed(c.audioUsedCount())
                .hasPlan(c.hasPlan())
                .hasIndictment(c.hasIndictment())
                .hasQualification(c.hasQualification())
                .totalPages(totalPages(c))
                .files(c.getFiles().stream()
                        .sorted(fileOrder())
                        .map(this::toFileResponse)
                        .collect(Collectors.toList()))
                .interrogations(c.getInterrogations().stream()
                        .map(interrogationMapper::toResponse)
                        .collect(Collectors.toList()))
                .users(c.getUsers().stream()
                        .map(u -> toUserResponse(u, c))
                        .collect(Collectors.toList()))
                .createdDate(c.getCreatedDate())
                .priority(taskQueueService.getCasePriority(c.getId()))
                .ownerFio(c.getOwner().getFio())
                .lastActivityDate(c.getLastActivityDate())
                .lastActivityType(c.getLastActivityType())
                .qualificationGeneratedAt(c.getQualificationGeneratedAt())
                .indictmentGeneratedAt(c.getIndictmentGeneratedAt())
                .updatedDate(c.getUpdatedDate())
                .build();
    }

    public CasePreviewResponse toPreview(Case c) {
        return CasePreviewResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .number(c.getNumber())
                .status(c.isStatus())
                .language(c.getLanguage())
                .createdDate(c.getCreatedDate())
                .updatedDate(c.getUpdatedDate())
                .ownerFio(c.getOwner().getFio())
                .build();
    }

    public CaseListResponse toListResponse(Case c) {
        return CaseListResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .number(c.getNumber())
                .status(c.isStatus())
                .hasPlan(c.hasPlan())
                .hasIndictment(c.hasIndictment())
                .hasQualification(c.hasQualification())
                .totalDocuments(c.getFiles().size())
                .totalPages(totalPages(c))
                .totalInterrogations(c.getInterrogations().size())
                .audioInterrogations(c.audioUsedCount())
                .createdDate(c.getCreatedDate())
                .lastActivityDate(c.getLastActivityDate())
                .lastActivityType(c.getLastActivityType())
                .priority(taskQueueService.getCasePriority(c.getId()))
                .ownerFio(c.getOwner() != null ? c.getOwner().getFio() : null)
                .participantFios(c.getUsers().stream().map(User::getFio).toList())
                .build();
    }

    public FigurantResponse toFigurantResponse(CaseFigurant figurant) {
        return FigurantResponse.builder()
                .id(figurant.getId())
                .externalId(figurant.getExternalId())
                .documentType(figurant.getDocumentType())
                .number(figurant.getNumber())
                .fio(figurant.getFio())
                .role(figurant.getRole())
                .details(figurant.getDetails())
                .references(figurant.getReferences() == null ? List.of()
                        : figurant.getReferences().stream()
                        .map(r -> FigurantReferenceResponse.builder()
                                .id(r.getId())
                                .referenceId(r.getReferenceId())
                                .filePath(r.getFilePath())
                                .build())
                        .toList())
                .build();
    }
    public CaseUserResponse toUserResponse(User user, Case c) {
        return CaseUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .surname(user.getSurname())
                .fathername(user.getFathername())
                .isOwner(c.isOwner(user))
                .build();
    }

    public CaseFileResponse toFileResponse(CaseFile f) {
        return CaseFileResponse.builder()
                .id(f.getId())
                .originalFileName(f.getOriginalFileName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .status(f.getStatus().getLabel())
                .language(f.getLanguage())
                .previewUrl(fileUrls.preview(f.getFileUrl()))
                .downloadUrl(fileUrls.download(f.getFileUrl(), f.getOriginalFileName()))
                .uploadedAt(String.valueOf(f.getUploadedAt()))
                .completedAt(String.valueOf(f.getCompletedAt()))
                .isQualification(f.isQualification())
                .tom(f.getTom())
                .pages(f.getPages() != null ? f.getPages() : 0)
                .startPage(f.getStartPage())
                .endPage(f.getEndPage())
                .assessmentSummary(f.getAssessmentSummary())
                .scorePercent(f.getScorePercent())
                .ownerFio(f.getUserEntity().getFio())
                .build();
    }
    public CaseMemberHistoryDto toCaseMemberHistoryDto(CaseMemberHistory h) {
            return CaseMemberHistoryDto.builder()
                    .action(h.getAction() != null ? h.getAction().getDescription() : null)
                    .targetFio(h.getTargetFio())
                    .targetEmail(h.getTargetEmail())
                    .performedByFio(h.getPerformedByFio())
                    .performedByEmail(h.getPerformedByEmail())
                    .timestamp(h.getTimestamp())
                    .build();
    }
    public RejectionReasonResponse toRejectionReasonResponse(RejectionReasonStatus r) {
        return RejectionReasonResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .status(r.isStatus())
                .rejectionReason(r.getRejectionReason())
                .performedByFio(r.getPerformedByFio())
                .timestamp(r.getTimestamp())
                .build();
    }

    private int totalPages(Case c) {
        return c.getFiles().stream()
                .filter(f -> f.getPages() != null)
                .mapToInt(CaseFile::getPages)
                .sum();
    }

    private Comparator<CaseFile> fileOrder() {
        return Comparator
                .comparing(CaseFile::getTom, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CaseFile::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CaseFile::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}