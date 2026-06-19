package org.di.digital.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrResultMessage;
import org.di.digital.model.enums.OsmotrProcessingStatus;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.service.impl.queue.OsmotrService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OsmotrResultConsumer {

    private final OsmotrService digitalOsmotrService;
    private final NotificationService notificationService;


    @RabbitListener(queues = "${spring.rabbitmq.osmotr.result.queue}")
    public void consume(OsmotrResultMessage message) {
        log.info("Received osmotr result: fileId={}, status={}", message.getFileId(), message.getStatus());

        try {
            notificationService.notifyOsmotrStatus(message);

            if (OsmotrProcessingStatus.COMPLETED.equals(message.getStatus())
                    || OsmotrProcessingStatus.FAILED.equals(message.getStatus())) {
                digitalOsmotrService.handleResult(
                        message.getFileId(),
                        message.getSessionId(),
                        message.getResult(),
                        message.getStatus(),
                        message.getErrorMessage(),
                        message.getProcessingDurationSeconds()
                );
            }
        } catch (Exception e) {
            log.error("Error handling osmotr result for fileId={}: {}", message.getFileId(), e.getMessage(), e);
        }
    }
}
