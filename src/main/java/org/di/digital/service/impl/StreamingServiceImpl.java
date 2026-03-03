package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.service.StreamingService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingServiceImpl implements StreamingService {

    private final WebClient.Builder webClientBuilder;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    @Override
    public void stream(
            String url,
            Object body,
            SseEmitter emitter,
            Function<String, String> chunkExtractor,
            Consumer<String> onComplete,
            Consumer<Throwable> onError
    ) {
        StringBuilder fullText = new StringBuilder();

        webClientBuilder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(DEFAULT_TIMEOUT)
                .doOnNext(chunk -> {
                    try {
                        String extracted = chunkExtractor.apply(chunk);
                        if (extracted != null && !extracted.isEmpty()) {
                            fullText.append(extracted);
                            emitter.send(SseEmitter.event().data(extracted));
                        }
                    } catch (Exception e) {
                        log.error("Error sending SSE chunk from {}: {}", url, e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    log.info("Streaming completed for url={}, total chars={}", url, fullText.length());
                    if (onComplete != null) {
                        onComplete.accept(fullText.toString());
                    }
                    emitter.complete();
                })
                .doOnError(error -> {
                    log.error("Streaming error for url={}: ", url, error);
                    if (onError != null) {
                        onError.accept(error);
                    }
                    emitter.completeWithError(error);
                })
                .subscribe();
    }

    @Override
    public void streamRaw(
            String url,
            Object body,
            SseEmitter emitter,
            Consumer<String> onComplete,
            Consumer<Throwable> onError
    ) {
        stream(url, body, emitter, chunk -> chunk, onComplete, onError);
    }
}