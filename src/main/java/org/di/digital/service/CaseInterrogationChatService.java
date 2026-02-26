package org.di.digital.service;

import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CaseInterrogationChatService {
    void streamInterrogationChatResponse(Long caseId, Long interrogationId, ChatRequest request, String userEmail, SseEmitter emitter);
    CaseChatHistoryResponse getChatHistory(Long caseId, Long interrogationId, String userEmail, int page, int size);
    void clearChatHistory(Long caseId, Long interrogationId, String userEmail);
}