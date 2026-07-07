package org.di.digital.service.impl.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrProcessingMessage;
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
public class OsmotrQueueService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.rabbitmq.osmotr.exchange}")
    private String osmotrExchange;

    @Value("${spring.rabbitmq.osmotr.routing-key}")
    private String osmotrRoutingKey;

    public void sendOsmotrForProcessing(OsmotrProcessingMessage payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);

            Message message = MessageBuilder
                    .withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

            rabbitTemplate.send(osmotrExchange, osmotrRoutingKey, message);

            log.info("Sent osmotr file {} for case {} to RabbitMQ",
                    payload.getOriginalFileName(), payload.getCaseNumber());

        } catch (Exception e) {
            throw new IllegalStateException("Failed to send osmotr message to RabbitMQ", e);
        }
    }
}
