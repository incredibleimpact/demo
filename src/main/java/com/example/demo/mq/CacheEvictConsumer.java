package com.example.demo.mq;

import com.example.demo.config.MQConfig;
import com.example.demo.utils.cache.CacheClient;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CacheEvictConsumer {
    @Resource
    private CacheClient cacheClient;

    @RabbitListener(queues = MQConfig.CACHE_EVICT_QUEUE)
    public void onMessage(CacheEvictMessage msg, Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            cacheClient.delete(msg.getCacheKey());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("cache evict failed, cacheKey={}", msg.getCacheKey(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
