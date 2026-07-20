package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.service.ChatService;
import org.di.digital.service.impl.core.sse.SseHeartbeatUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/cases/chat")
@RequiredArgsConstructor
public class CaseChatController {

    private final ChatService chatService;
    private final SseHeartbeatUtil heartbeatUtil;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String caseNumber,
            @Valid @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        log.info("Starting chat stream for case: {} by user: {}", caseNumber, authentication.getName());

        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "case-" + caseNumber);
        chatService.streamCaseChatResponseWithHistory(caseNumber, request, authentication.getName(), emitter);
        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<CaseChatHistoryResponse> getChatHistory(
            @RequestParam String caseNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        log.info("Getting chat history for case: {} by user: {}", caseNumber, authentication.getName());

        CaseChatHistoryResponse response = chatService.getChatHistoryByCaseNumber(
                caseNumber,
                authentication.getName(),
                page,
                size
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearChatHistory(
            @RequestParam String caseNumber,
            Authentication authentication
    ) {
        log.info("Clearing chat history for case: {} by user: {}", caseNumber, authentication.getName());

        chatService.clearChatHistoryByCaseNumber(caseNumber, authentication.getName());

        return ResponseEntity.noContent().build();
    }
}