package org.di.digital.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.service.StreamingService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.di.digital.util.SystemParams.CHUNK_SIZE;
import static org.di.digital.util.SystemParams.STREAM_DELAY_MS;

@Slf4j
@Service
public class StreamingServiceImpl implements StreamingService {

    @Override
    public void streamText(SseEmitter emitter, String text) throws IOException {
        for (int i = 0; i < text.length(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, text.length());
            String chunk = text.substring(i, end);

            emitter.send(SseEmitter.event().data(chunk));
            sleepBetweenChunks();
        }
    }

    private void sleepBetweenChunks() throws IOException {
        try {
            Thread.sleep(STREAM_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Streaming interrupted", e);
        }
    }
}