package org.di.digital.config.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterrogationRabbitConfig {

    // Outgoing — отправка аудио в interrogation сервис
    @Value("${spring.rabbitmq.inter.queue}")
    public String INTERROGATION_QUEUE;

    @Value("${spring.rabbitmq.inter.exchange}")
    public String INTERROGATION_EXCHANGE;

    @Value("${spring.rabbitmq.inter.routing-key}")
    public String INTERROGATION_ROUTING_KEY;

    // DLQ
    @Value("${spring.rabbitmq.dlq.exchange}")
    public String DLQ_EXCHANGE;

    @Value("${spring.rabbitmq.dlq.routing-key}")
    public String DLQ_ROUTING_KEY;

    // Incoming — результат транскрипции
    @Value("${spring.rabbitmq.inter.result.queue}")
    public String INTERROGATION_RESULT_QUEUE;

    @Value("${spring.rabbitmq.inter.result.exchange}")
    public String INTERROGATION_RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.inter.result.routing-key}")
    public String INTERROGATION_RESULT_ROUTING_KEY;

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
        return BindingBuilder.bind(interrogationQueue).to(interrogationExchange).with(INTERROGATION_ROUTING_KEY);
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
    public Binding interrogationResultBinding(Queue interrogationResultQueue,
                                              DirectExchange interrogationResultExchange) {
        return BindingBuilder.bind(interrogationResultQueue)
                .to(interrogationResultExchange)
                .with(INTERROGATION_RESULT_ROUTING_KEY);
    }
}