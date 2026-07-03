package com.linkup.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Subscribes this pod's FanoutSubscriber to the Redis fan-out channel. */
@Configuration
public class RedisFanoutConfig {

    @Bean
    RedisMessageListenerContainer fanoutListenerContainer(RedisConnectionFactory connectionFactory,
                                                          FanoutSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RealtimeFanout.CHANNEL));
        return container;
    }
}
