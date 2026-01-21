package org.di.digital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.model.CaseChatMessage;
import org.di.digital.model.MessageRole;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseChatMessageDto {
    private Long id;
    private MessageRole role;
    private String content;
    private LocalDateTime createdDate;
    private boolean complete;

    public static CaseChatMessageDto from(CaseChatMessage message) {
        return CaseChatMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdDate(message.getCreatedDate())
                .complete(message.isComplete())
                .build();
    }
}