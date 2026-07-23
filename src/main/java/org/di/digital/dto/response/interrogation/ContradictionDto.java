package org.di.digital.dto.response.interrogation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.dto.response.cases.ReferenceDto;
import org.di.digital.dto.response.chat.ReferenceLinkDto;
import org.di.digital.model.interrogation.CaseInterrogationContradiction;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContradictionDto {

    private Long id;
    private Long sourceMessageId;
    private String text;
    private Integer confidencePercent;
    private LocalDateTime createdDate;
    private List<ReferenceLinkDto> references;

    public static ContradictionDto from(CaseInterrogationContradiction c) {
        List<ReferenceLinkDto> refs = c.getReferences() == null
                ? Collections.emptyList()
                : c.getReferences().stream()
                .map(ContradictionDto::toLink)
                .toList();

        return ContradictionDto.builder()
                .id(c.getId())
                .sourceMessageId(c.getSourceMessageId())
                .text(c.getText())
                .confidencePercent(c.getConfidencePercent())
                .createdDate(c.getCreatedDate())
                .references(refs)
                .build();
    }

    private static ReferenceLinkDto toLink(ReferenceDto ref) {
        return ReferenceLinkDto.builder()
                .referenceId(ref.getReferenceId())
                .link(ref.getFilePath())
                .opis(ref.getOpis())
                .build();
    }
}