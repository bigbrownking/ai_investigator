package org.di.digital.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.ChatRequest;
import org.di.digital.dto.response.ChatResponse;
import org.di.digital.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Stream chat responses using Server-Sent Events (SSE)
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getQuery());

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executor.execute(() -> {
            try {
                chatService.streamChatResponse(request, emitter);
            } catch (Exception e) {
                log.error("Error during streaming: ", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Stream chat responses using Reactive Flux
     */
    @PostMapping(value = "/stream-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatFlux(@RequestBody ChatRequest request) {
        log.info("Received chat request (Flux): {}", request.getQuery());
        return chatService.streamChatResponseFlux(request);
    }

    /**
     * Non-streaming chat endpoint (waits for complete response)
     */
    @PostMapping("/complete")
    public ResponseEntity<ChatResponse> completeChat(@RequestBody ChatRequest request) {
        log.info("Received complete chat request: {}", request.getQuery());
        ChatResponse response = chatService.getChatResponse(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check for Python model service
     */
    @GetMapping("/health")
    public ResponseEntity<String> checkHealth() {
        boolean isHealthy = chatService.checkModelHealth();
        if (isHealthy) {
            return ResponseEntity.ok("Model service is healthy");
        } else {
            return ResponseEntity.status(503).body("Model service is unavailable");
        }
    }
}