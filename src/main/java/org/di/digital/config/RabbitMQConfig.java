package org.di.digital.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Outgoing - для отправки задач в mediator
    public static final String DOCUMENT_QUEUE = "document.processing.queue";
    public static final String DOCUMENT_EXCHANGE = "document.exchange";
    public static final String DOCUMENT_ROUTING_KEY = "document.process";

    // DLQ
    public static final String DLQ_QUEUE = "document.dlq";
    public static final String DLQ_EXCHANGE = "document.dlq.exchange";
    public static final String DLQ_ROUTING_KEY = "document.dlq";

    // Incoming - для получения результатов от mediator
    public static final String RESULT_QUEUE = "document.result.queue";
    public static final String RESULT_EXCHANGE = "document.result.exchange";
    public static final String RESULT_PROCESSING_ROUTING_KEY = "document.result.processing";
    public static final String RESULT_SUCCESS_ROUTING_KEY = "document.result.success";
    public static final String RESULT_FAILURE_ROUTING_KEY = "document.result.failure";

    // Outgoing - отправка аудио в interrogation сервис
    public static final String INTERROGATION_QUEUE = "interrogation.processing.queue";
    public static final String INTERROGATION_EXCHANGE = "interrogation.exchange";
    public static final String INTERROGATION_ROUTING_KEY = "interrogation.process";

    // Incoming - получение результата транскрипции от interrogation сервиса
    public static final String INTERROGATION_RESULT_QUEUE = "interrogation.result.queue";
    public static final String INTERROGATION_RESULT_EXCHANGE = "interrogation.result.exchange";
    public static final String INTERROGATION_RESULT_ROUTING_KEY = "interrogation.result.transcribed";


    // ==================== Outgoing Configuration ====================

    @Bean
    public Queue documentQueue() {
        return QueueBuilder.durable(DOCUMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange documentExchange() {
        return new DirectExchange(DOCUMENT_EXCHANGE);
    }

    @Bean
    public Binding documentBinding(Queue documentQueue, DirectExchange documentExchange) {
        return BindingBuilder
                .bind(documentQueue)
                .to(documentExchange)
                .with(DOCUMENT_ROUTING_KEY);
    }

    // ==================== Incoming Configuration ====================

    @Bean
    public Queue resultQueue() {
        return QueueBuilder.durable(RESULT_QUEUE).build();
    }

    @Bean
    public DirectExchange resultExchange() {
        return new DirectExchange(RESULT_EXCHANGE);
    }

    @Bean
    public Binding resultProcessingBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder
                .bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_PROCESSING_ROUTING_KEY);
    }

    @Bean
    public Binding resultSuccessBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder
                .bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_SUCCESS_ROUTING_KEY);
    }

    @Bean
    public Binding resultFailureBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder
                .bind(resultQueue)
                .to(resultExchange)
                .with(RESULT_FAILURE_ROUTING_KEY);
    }

    // ==================== Common Configuration ====================

    @Bean
    @SuppressWarnings("deprecation")
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public Queue interrogationQueue() {
        return QueueBuilder.durable(INTERROGATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange interrogationExchange() {
        return new DirectExchange(INTERROGATION_EXCHANGE);
    }

    @Bean
    public Binding interrogationBinding(Queue interrogationQueue, DirectExchange interrogationExchange) {
        return BindingBuilder
                .bind(interrogationQueue)
                .to(interrogationExchange)
                .with(INTERROGATION_ROUTING_KEY);
    }

    @Bean
    public Queue interrogationResultQueue() {
        return QueueBuilder.durable(INTERROGATION_RESULT_QUEUE).build();
    }

    @Bean
    public DirectExchange interrogationResultExchange() {
        return new DirectExchange(INTERROGATION_RESULT_EXCHANGE);
    }

    @Bean
    public Binding interrogationResultBinding(Queue interrogationResultQueue, DirectExchange interrogationResultExchange) {
        return BindingBuilder
                .bind(interrogationResultQueue)
                .to(interrogationResultExchange)
                .with(INTERROGATION_RESULT_ROUTING_KEY);
    }

}