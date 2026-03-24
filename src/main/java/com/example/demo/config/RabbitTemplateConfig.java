package com.example.demo.config;

import com.example.demo.utils.constants.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@Slf4j
public class RabbitTemplateConfig {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        rabbitTemplate.setMandatory(true);

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData != null ? correlationData.getId() : null;
            if (ack) {
                String key = RedisConstants.MQ_CONFIRM_CACHE_KEY + id;
                stringRedisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(30));
                log.info("MQ message reached exchange, msgId={}", id);
            } else {
                log.error("MQ message failed to reach exchange, msgId={}, cause={}", id, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            if (MQConfig.ORDER_DELAY_EXCHANGE.equals(returned.getExchange())
                    && "NO_ROUTE".equals(returned.getReplyText())) {
                log.debug("Ignore delayed exchange return callback, exchange={}, routingKey={}, msg={}",
                        returned.getExchange(),
                        returned.getRoutingKey(),
                        new String(returned.getMessage().getBody(), StandardCharsets.UTF_8));
                return;
            }
            log.error("MQ route to queue failed, exchange={}, routingKey={}, replyText={}, msg={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyText(),
                    new String(returned.getMessage().getBody(), StandardCharsets.UTF_8));
        });

        return rabbitTemplate;
    }
}
