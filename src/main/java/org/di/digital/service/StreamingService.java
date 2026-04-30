package org.di.digital.service;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public interface StreamingService {

    void stream(
            String url,
            Object body,
            SseEmitter emitter,
            Function<String, String> chunkExtractor,
            Consumer<String> onComplete,
            Consumer<Throwable> onError
    );
    void stream(
            String url,
            Object body,
            SseEmitter emitter,
            Function<String, String> chunkExtractor,
            Consumer<String> onComplete,
            Consumer<Throwable> onError,
            boolean addParagraphSeparator
    );

    void streamRaw(
            String url,
            Object body,
            SseEmitter emitter,
            Consumer<String> onComplete,
            Consumer<Throwable> onError
    );
}