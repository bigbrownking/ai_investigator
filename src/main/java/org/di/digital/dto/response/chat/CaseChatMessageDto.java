package org.di.digital.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.dto.response.cases.ReferenceDto;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.MessageRole;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseChatMessageDto {
    private Long id;
    private MessageRole role;
    private Boolean edited;
    private Boolean selected;
    private String content;
    private LocalDateTime createdDate;
    private boolean complete;
    private List<ReferenceLinkDto> references;

    public static CaseChatMessageDto from(CaseChatMessage message) {
        List<ReferenceLinkDto> refs = message.getReferences() == null
                ? Collections.emptyList()
                : message.getReferences().stream()
                .map(CaseChatMessageDto::toLink)
                .toList();

        return CaseChatMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .edited(message.getIsEdited())
                .selected(message.getIsSelected())
                .createdDate(message.getCreatedDate())
                .complete(message.isComplete())
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