package org.di.digital.config.face;

import lombok.RequiredArgsConstructor;
import org.di.digital.service.face.FaceJobRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;

@Configuration
@RequiredArgsConstructor
public class FaceRedisSubscriberConfig {

    private final FaceJobRedisSubscriber subscriber;

    @Bean
    public RedisMessageListenerContainer faceJobListenerContainer(RedisConnectionFactory cf) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.addMessageListener(subscriber, new ChannelTopic("face-job-events"));
        return container;
    }
}