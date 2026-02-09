package org.di.digital.service;

import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    void streamCaseChatResponseWithHistory(String caseNumber, ChatRequest request, String userEmail, SseEmitter emitter);

    void streamChatResponse(ChatRequest request, SseEmitter emitter);

    CaseChatHistoryResponse getChatHistoryByCaseNumber(String caseNumber, String userEmail, int page, int size);

    CaseChatHistoryResponse getChatHistory(Long caseId, Long userId, int page, int size);

    void clearChatHistoryByCaseNumber(String caseNumber, String userEmail);

    void clearChatHistory(Long caseId, Long userId);
}