package org.di.digital.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public interface StreamingService {
    void streamText(SseEmitter emitter, String text) throws IOException;
}