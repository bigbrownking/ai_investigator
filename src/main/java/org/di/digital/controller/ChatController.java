package org.di.digital.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.cases.ChatRequest;
import org.di.digital.service.cases.ChatService;
import org.di.digital.service.impl.core.sse.SseHeartbeatUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final SseHeartbeatUtil heartbeatUtil;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGeneralChat(@Valid @RequestBody ChatRequest request) {
        String question = request.getQuestion();
        log.info("Received general chat request: {}",
                question.substring(0, Math.min(50, question.length())));

        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(10));
        heartbeatUtil.startHeartbeat(emitter, "general-chat");
        chatService.streamChatResponse(request, emitter);
        return emitter;
    }
}