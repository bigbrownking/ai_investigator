package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.dto.response.chat.CaseChatHistoryResponse;
import org.di.digital.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/cases/chat")
@RequiredArgsConstructor
public class CaseChatController {

    private final ChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String caseNumber,
            @Valid @RequestBody ChatRequest request,
            Authentication authentication
    ) {
        log.info("Starting chat stream for case: {} by user: {}", caseNumber, authentication.getName());

        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));

        emitter.onCompletion(() -> log.info("Chat stream completed for case: {}", caseNumber));
        emitter.onTimeout(() -> {
            log.warn("Chat stream timed out for case: {}", caseNumber);
            emitter.complete();
        });
        emitter.onError(e -> log.error("Chat stream error for case: {}", caseNumber, e));

        try {
            emitter.send(SseEmitter.event()
                    .data("Chat stream started"));
        } catch (IOException e) {
            log.error("Failed to send initial event", e);
        }

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