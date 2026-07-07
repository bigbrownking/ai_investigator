package org.di.digital.service.impl.core;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SseHeartbeatUtil {

    public ScheduledFuture<?> startHeartbeat(SseEmitter emitter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat").build());
            } catch (IOException e) {
                scheduler.shutdown();
            }
        }, 15, 25, TimeUnit.SECONDS);

        emitter.onCompletion(() -> { future.cancel(true); scheduler.shutdown(); });
        emitter.onTimeout(() -> { future.cancel(true); scheduler.shutdown(); });
        emitter.onError(e -> { future.cancel(true); scheduler.shutdown(); });

        return future;
    }
}
