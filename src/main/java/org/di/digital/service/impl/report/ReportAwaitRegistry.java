package org.di.digital.service.impl.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ReportAwaitRegistry {

    private final Map<Long, CompletableFuture<ReportOutcome>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<ReportOutcome> register(Long reviewId) {
        CompletableFuture<ReportOutcome> future = new CompletableFuture<>();
        pending.put(reviewId, future);
        return future;
    }

    public void complete(Long reviewId, ReportOutcome outcome) {
        CompletableFuture<ReportOutcome> future = pending.remove(reviewId);
        if (future != null) {
            future.complete(outcome);
        } else {
            log.debug("No waiter for reviewId={} (already timed out or async call)", reviewId);
        }
    }

    public void cancel(Long reviewId) {
        pending.remove(reviewId);
    }

    public record ReportOutcome(boolean success, String reportFileUrl, String errorMessage) {}
}