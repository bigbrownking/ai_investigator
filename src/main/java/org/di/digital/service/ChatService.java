package org.di.digital.service;

import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

public interface ChatService {
    void streamChatResponse(ChatRequest request, SseEmitter emitter);
    Flux<String> streamChatResponseFlux(ChatRequest request);
    ChatResponse getChatResponse(ChatRequest request);
    boolean checkModelHealth();
}
