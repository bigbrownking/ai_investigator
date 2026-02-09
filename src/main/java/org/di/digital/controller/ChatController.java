package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {
    private final ChatService chatService;
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGeneralChat(@Valid @RequestBody ChatRequest request) {
        log.info("🤖 Received general chat request: {}",
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        SseEmitter emitter = new SseEmitter(0L);

        try {
            chatService.streamChatResponse(request, emitter);
        } catch (Exception e) {
            log.error("❌ Error creating general chat stream: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

}
