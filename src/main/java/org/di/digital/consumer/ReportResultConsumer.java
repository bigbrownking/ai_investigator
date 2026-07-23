package org.di.digital.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.ReportResultMessage;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.service.report.ReportService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportResultConsumer {

    private final ReportService reportService;
    private final NotificationService notificationService;

    @RabbitListener(queues = "${spring.rabbitmq.report.result.queue}")
    public void handleReportResult(ReportResultMessage message) {
        log.info("Received {} report notification for case {}",
                message.getStatus(), message.getCaseNumber());
        try {
            switch (message.getStatus()) {
                case PROCESSING -> handleProcessing(message);
                case COMPLETED -> handleCompletion(message);
                case FAILED -> handleFailure(message);
            }
        } catch (Exception e) {
            log.error("Error handling report result for case {}: {}",
                    message.getCaseNumber(), e.getMessage(), e);
        }
    }

    private void handleProcessing(ReportResultMessage message) {
        reportService.saveProcessing(message);
        notificationService.notifyReportStatus(message);
    }

    private void handleCompletion(ReportResultMessage message) {
        reportService.saveCompleted(message);
        notificationService.notifyReportStatus(message);
    }

    private void handleFailure(ReportResultMessage message) {
        reportService.saveFailed(message);
        notificationService.notifyReportStatus(message);
    }
}