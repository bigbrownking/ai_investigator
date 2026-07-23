package org.di.digital.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.dto.response.interrogation.ContradictionDto;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseChatHistoryResponse {
    private Long chatId;
    private Long caseId;
    private List<CaseChatMessageDto> messages;
    private List<ContradictionDto> contradictions;
    private int totalMessages;
    private int currentPage;
    private int pageSize;
    private LocalDateTime lastMessageAt;
}