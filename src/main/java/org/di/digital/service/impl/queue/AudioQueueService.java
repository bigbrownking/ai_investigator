package org.di.digital.service.impl.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.config.RabbitMQConfig;
import org.di.digital.dto.message.AudioProcessingMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioQueueService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void sendAudioForProcessing(AudioProcessingMessage payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);

            Message message = MessageBuilder
                    .withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

            rabbitTemplate.send(
                    RabbitMQConfig.INTERROGATION_EXCHANGE,
                    RabbitMQConfig.INTERROGATION_ROUTING_KEY,
                    message
            );

            log.info("Sent audio {} for interrogation: {} to RabbitMQ",
                    payload.getOriginalFileName(), payload.getInterrogationId());

        } catch (Exception e) {
            throw new IllegalStateException("Failed to send audio message to RabbitMQ", e);
        }
    }
}