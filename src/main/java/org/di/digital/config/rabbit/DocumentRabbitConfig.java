package org.di.digital.config.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentRabbitConfig {

    // Outgoing — отправка задач в mediator
    @Value("${spring.rabbitmq.mediator.queue}")
    public String DOCUMENT_QUEUE;

    @Value("${spring.rabbitmq.mediator.exchange}")
    public String DOCUMENT_EXCHANGE;

    @Value("${spring.rabbitmq.mediator.routing-key}")
    public String DOCUMENT_ROUTING_KEY;

    // DLQ (нужно для аргументов очереди)
    @Value("${spring.rabbitmq.dlq.exchange}")
    public String DLQ_EXCHANGE;

    @Value("${spring.rabbitmq.dlq.routing-key}")
    public String DLQ_ROUTING_KEY;

    // Incoming — результаты от mediator
    @Value("${spring.rabbitmq.mediator.result.queue}")
    public String RESULT_QUEUE;

    @Value("${spring.rabbitmq.mediator.result.exchange}")
    public String RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.mediator.result.routing-key}")
    public String RESULT_PROCESSING_ROUTING_KEY;

    @Value("${spring.rabbitmq.mediator.result.success.routing-key}")
    public String RESULT_SUCCESS_ROUTING_KEY;

    @Value("${spring.rabbitmq.mediator.result.failure.routing-key}")
    public String RESULT_FAILURE_ROUTING_KEY;

    // ===== Outgoing =====

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
        return BindingBuilder.bind(documentQueue).to(documentExchange).with(DOCUMENT_ROUTING_KEY);
    }

    // ===== Incoming =====

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
        return BindingBuilder.bind(resultQueue).to(resultExchange).with(RESULT_PROCESSING_ROUTING_KEY);
    }

    @Bean
    public Binding resultSuccessBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder.bind(resultQueue).to(resultExchange).with(RESULT_SUCCESS_ROUTING_KEY);
    }

    @Bean
    public Binding resultFailureBinding(Queue resultQueue, DirectExchange resultExchange) {
        return BindingBuilder.bind(resultQueue).to(resultExchange).with(RESULT_FAILURE_ROUTING_KEY);
    }
}