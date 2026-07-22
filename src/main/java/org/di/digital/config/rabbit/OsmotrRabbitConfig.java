package org.di.digital.config.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OsmotrRabbitConfig {

    // Outgoing — отправка в osmotr сервис
    @Value("${spring.rabbitmq.osmotr.queue}")
    public String OSMOTR_QUEUE;

    @Value("${spring.rabbitmq.osmotr.exchange}")
    public String OSMOTR_EXCHANGE;

    @Value("${spring.rabbitmq.osmotr.routing-key}")
    public String OSMOTR_ROUTING_KEY;

    // DLQ
    @Value("${spring.rabbitmq.dlq.exchange}")
    public String DLQ_EXCHANGE;

    @Value("${spring.rabbitmq.dlq.routing-key}")
    public String DLQ_ROUTING_KEY;

    // Incoming — результат osmotr
    @Value("${spring.rabbitmq.osmotr.result.queue}")
    public String OSMOTR_RESULT_QUEUE;

    @Value("${spring.rabbitmq.osmotr.result.exchange}")
    public String OSMOTR_RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.osmotr.result.routing-key}")
    public String OSMOTR_RESULT_ROUTING_KEY;

    @Bean
    public Queue osmotrQueue() {
        return QueueBuilder.durable(OSMOTR_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange osmotrExchange() {
        return new DirectExchange(OSMOTR_EXCHANGE);
    }

    @Bean
    public Binding osmotrBinding(Queue osmotrQueue, DirectExchange osmotrExchange) {
        return BindingBuilder.bind(osmotrQueue).to(osmotrExchange).with(OSMOTR_ROUTING_KEY);
    }

    @Bean
    public Queue osmotrResultQueue() {
        return QueueBuilder.durable(OSMOTR_RESULT_QUEUE).build();
    }

    @Bean
    public DirectExchange osmotrResultExchange() {
        return new DirectExchange(OSMOTR_RESULT_EXCHANGE);
    }

    @Bean
    public Binding osmotrResultBinding(Queue osmotrResultQueue, DirectExchange osmotrResultExchange) {
        return BindingBuilder.bind(osmotrResultQueue)
                .to(osmotrResultExchange)
                .with(OSMOTR_RESULT_ROUTING_KEY);
    }
}