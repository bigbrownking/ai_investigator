package org.di.digital.service.impl.core.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SseHeartbeatUtil {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(4);

    public ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String context) {
        ScheduledFuture<?> future = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat").build());
            } catch (Exception ignored) {
            }
        }, 15, 25, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {
            future.cancel(true);
            log.info("SSE completed: {}", context);
        });
        emitter.onTimeout(() -> {
            future.cancel(true);
            log.warn("SSE timed out: {}", context);
            emitter.complete();
        });
        emitter.onError(e -> {
            future.cancel(true);
            log.error("SSE error: {}", context, e);
        });

        return future;
    }
}
