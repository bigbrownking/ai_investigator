package org.di.digital.service.interrogation;

import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CaseInterrogationChatService {
    void streamInterrogationChatResponse(Long caseId, Long interrogationId, ChatRequest request, String userEmail, SseEmitter emitter);
    CaseChatHistoryResponse getChatHistory(Long caseId, Long interrogationId, String userEmail, int page, int size);
    void clearChatHistory(Long caseId, Long interrogationId, String userEmail);
    void toggleMessageSelected(Long caseId, Long interrogationId, Long messageId, boolean selected, String userEmail);
    void streamCaseInterrogationChatResponse(Long caseId, Long interrogationId, ChatRequest request, String userEmail, SseEmitter emitter);
    CaseChatHistoryResponse getCaseInterrogationChatHistory(Long caseId, Long interrogationId, String userEmail, int page, int size);
    void clearCaseInterrogationChatHistory(Long caseId, Long interrogationId, String userEmail);
}