package org.di.digital.service.impl.core.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SseTypingEmitter {

    @Value("${chat.typing.delay-ms:20}")
    private long typingDelayMs;

    public void emitTyping(SseEmitter emitter, String text) throws IOException, InterruptedException {
        for (String token : text.split("(?<= )")) {
            emitter.send(SseEmitter.event().data(token));
            if (typingDelayMs > 0) Thread.sleep(typingDelayMs);
        }
    }
}
