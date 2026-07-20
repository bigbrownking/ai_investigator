package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.service.impl.core.sse.SseHeartbeatUtil;
import org.di.digital.service.interrogation.CaseInterrogationChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseInterrogationChatController {

    private final SseHeartbeatUtil heartbeatUtil;
    private final CaseInterrogationChatService interrogationChatService;

    @PostMapping(value = "/{caseId}/interrogations/{interrogationId}/chat",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "interrogation-" + interrogationId);

        interrogationChatService.streamInterrogationChatResponse(
                caseId, interrogationId, request, authentication.getName(), emitter);
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

    @PatchMapping("/{caseId}/interrogations/{interrogationId}/chat/messages/{messageId}/select")
    public ResponseEntity<Void> toggleMessageSelected(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @PathVariable Long messageId,
            @RequestParam boolean selected,
            Authentication authentication
    ) {
        interrogationChatService.toggleMessageSelected(caseId, interrogationId, messageId, selected, authentication.getName());
        return ResponseEntity.noContent().build();
    }




    @PostMapping(value = "/{caseId}/interrogations/{interrogationId}/case-chat",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCaseChat(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestBody ChatRequest request,
            Authentication authentication) {
        log.info("Starting case chat stream for interrogation {} in case {}", interrogationId, caseId);

        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "interrogation-case-chat-" + interrogationId);

        interrogationChatService.streamCaseInterrogationChatResponse(
                caseId, interrogationId, request, authentication.getName(), emitter);
        return emitter;
    }

    @GetMapping("/{caseId}/interrogations/{interrogationId}/case-chat/history")
    public ResponseEntity<CaseChatHistoryResponse> getCaseChatHistory(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return ResponseEntity.ok(
                interrogationChatService.getCaseInterrogationChatHistory(
                        caseId, interrogationId, authentication.getName(), page, size));
    }

    @DeleteMapping("/{caseId}/interrogations/{interrogationId}/case-chat/history")
    public ResponseEntity<Void> clearCaseChatHistory(
            @PathVariable Long caseId,
            @PathVariable Long interrogationId,
            Authentication authentication) {
        interrogationChatService.clearCaseInterrogationChatHistory(
                caseId, interrogationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}