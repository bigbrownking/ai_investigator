package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.CaseChatHistoryResponse;
import org.di.digital.service.CaseInterrogationChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseInterrogationChatController {

    private final CaseInterrogationChatService interrogationChatService;

    @PostMapping(value = "/{caseId}/interrogations/{interrogationId}/chat",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        SseEmitter emitter = new SseEmitter(0L);
        log.info("Starting chat stream for interrogation {} in case {}", interrogationId, caseId);
        interrogationChatService.streamInterrogationChatResponse(
                caseId, interrogationId, request, authentication.getName(), emitter
        );
        return emitter;
    }

    @GetMapping("/{caseId}/interrogations/{interrogationId}/chat/history")
    public ResponseEntity<CaseChatHistoryResponse> getChatHistory(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                interrogationChatService.getChatHistory(caseId, interrogationId, authentication.getName(), page, size)
        );
    }

    @DeleteMapping("/{caseId}/interrogations/{interrogationId}/chat/history")
    public ResponseEntity<Void> clearChatHistory(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            Authentication authentication
    ) {
        interrogationChatService.clearChatHistory(caseId, interrogationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}