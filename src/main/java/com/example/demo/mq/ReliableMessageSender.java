package com.example.demo.mq;

import com.example.demo.config.MQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReliableMessageSender {

    private final RabbitTemplate rabbitTemplate;

    public void sendOrderCreate(VoucherOrderCreateMessage msg) {
        CorrelationData correlationData = new CorrelationData(msg.getMsgId());
        rabbitTemplate.convertAndSend(
                MQConfig.ORDER_EVENT_EXCHANGE,
                MQConfig.ORDER_CREATE_ROUTING_KEY,
                msg,
                message -> {
                    message.getMessageProperties().setMessageId(msg.getMsgId());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
        );
    }

    public void sendPayCheck(OrderPayCheckMessage msg, long delayMillis) {
        CorrelationData correlationData = new CorrelationData(msg.getMsgId());
        rabbitTemplate.convertAndSend(
                MQConfig.ORDER_DELAY_EXCHANGE,
                MQConfig.ORDER_PAY_CHECK_ROUTING_KEY,
                msg,
                message -> {
                    message.getMessageProperties().setMessageId(msg.getMsgId());
                    message.getMessageProperties().setDelay(Math.toIntExact(delayMillis));
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
        );
    }

    public void sendCacheEvict(CacheEvictMessage msg, long delayMillis) {
        CorrelationData correlationData = new CorrelationData(msg.getMsgId());
        rabbitTemplate.convertAndSend(
                MQConfig.ORDER_DELAY_EXCHANGE,
                MQConfig.CACHE_EVICT_ROUTING_KEY,
                msg,
                message -> {
                    message.getMessageProperties().setMessageId(msg.getMsgId());
                    message.getMessageProperties().setDelay(Math.toIntExact(delayMillis));
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
        );
    }
}
