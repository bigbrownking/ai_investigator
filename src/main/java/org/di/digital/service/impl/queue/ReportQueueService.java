package org.di.digital.service.impl.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.ReportProcessingMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportQueueService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.rabbitmq.report.exchange}")
    private String reportExchange;

    @Value("${spring.rabbitmq.report.routing-key}")
    private String reportRoutingKey;

    public void sendReportForProcessing(ReportProcessingMessage payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);

            Message message = MessageBuilder
                    .withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

            rabbitTemplate.send(reportExchange, reportRoutingKey, message);

            log.info("Sent report for case {} to RabbitMQ", payload.getCaseNumber());

        } catch (Exception e) {
            throw new IllegalStateException("Failed to send report message to RabbitMQ", e);
        }
    }
}