package org.di.digital.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.model.cases.CaseChatMessage;
import org.di.digital.model.enums.MessageRole;

import java.time.LocalDateTime;

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

    public static CaseChatMessageDto from(CaseChatMessage message) {
        return CaseChatMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .edited(message.getIsEdited())
                .selected(message.getIsSelected())
                .createdDate(message.getCreatedDate())
                .complete(message.isComplete())
                .build();
    }
}