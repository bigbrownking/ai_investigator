package org.di.digital.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Outgoing - для отправки задач в mediator

    @Value("${spring.rabbitmq.mediator.queue}")
    public String DOCUMENT_QUEUE;

    @Value("${spring.rabbitmq.mediator.exchange}")
    public  String DOCUMENT_EXCHANGE;

    @Value("${spring.rabbitmq.mediator.routing-key}")
    public  String DOCUMENT_ROUTING_KEY;

    // DLQ
    @Value("${spring.rabbitmq.dlq.queue}")
    public  String DLQ_QUEUE;
    @Value("${spring.rabbitmq.dlq.exchange}")
    public  String DLQ_EXCHANGE;

    @Value("${spring.rabbitmq.dlq.routing-key}")
    public  String DLQ_ROUTING_KEY;

    // Incoming - для получения результатов от mediator

    @Value("${spring.rabbitmq.mediator.result.queue}")
    public  String RESULT_QUEUE;

    @Value("${spring.rabbitmq.mediator.result.exchange}")
    public  String RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.mediator.result.routing-key}")
    public  String RESULT_PROCESSING_ROUTING_KEY;

    @Value("${spring.rabbitmq.mediator.result.success.routing-key}")
    public  String RESULT_SUCCESS_ROUTING_KEY;

    @Value("${spring.rabbitmq.mediator.result.failure.routing-key}")
    public  String RESULT_FAILURE_ROUTING_KEY;

    // Outgoing - отправка аудио в interrogation сервис

    @Value("${spring.rabbitmq.inter.queue}")
    public  String INTERROGATION_QUEUE;

    @Value("${spring.rabbitmq.inter.exchange}")
    public  String INTERROGATION_EXCHANGE;

    @Value("${spring.rabbitmq.inter.routing-key}")
    public  String INTERROGATION_ROUTING_KEY;

    // Incoming - получение результата транскрипции от interrogation сервиса
    @Value("${spring.rabbitmq.inter.result.queue}")
    public  String INTERROGATION_RESULT_QUEUE;

    @Value("${spring.rabbitmq.inter.result.exchange}")
    public  String INTERROGATION_RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.inter.result.routing-key}")
    public  String INTERROGATION_RESULT_ROUTING_KEY;


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

    @Bean
    public RabbitAdmin rabbitAdmin(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}