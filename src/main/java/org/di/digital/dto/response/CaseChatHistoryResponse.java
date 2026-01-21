package org.di.digital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.di.digital.model.CaseChatMessage;

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
    private int totalMessages;
    private int currentPage;
    private int pageSize;
    private LocalDateTime lastMessageAt;
}