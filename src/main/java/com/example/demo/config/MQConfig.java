package com.example.demo.config;

import org.springframework.amqp.core.*;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MQConfig {

    public static final String ORDER_EVENT_EXCHANGE = "order.event.exchange";
    public static final String ORDER_CREATE_QUEUE = "voucher.order.create.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "voucher.order.create";

    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_PAY_CHECK_QUEUE = "voucher.order.pay.check.queue";
    public static final String ORDER_PAY_CHECK_ROUTING_KEY = "voucher.order.pay.check";
    public static final String CACHE_EVICT_QUEUE = "cache.evict.queue";
    public static final String CACHE_EVICT_ROUTING_KEY = "cache.evict";

    public static final String ORDER_DLX_EXCHANGE = "order.dlx.exchange";
    public static final String ORDER_DLX_QUEUE = "voucher.order.dlq";
    public static final String ORDER_DLX_ROUTING_KEY = "voucher.order.dlx";

    @Bean
    public DirectExchange orderEventExchange() {
        return ExchangeBuilder.directExchange(ORDER_EVENT_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(ORDER_CREATE_QUEUE)
                .deadLetterExchange(ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue())
                .to(orderEventExchange())
                .with(ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(ORDER_DELAY_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue orderPayCheckQueue() {
        return QueueBuilder.durable(ORDER_PAY_CHECK_QUEUE)
                .deadLetterExchange(ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderPayCheckBinding() {
        return BindingBuilder.bind(orderPayCheckQueue())
                .to(orderDelayExchange())
                .with(ORDER_PAY_CHECK_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public Queue cacheEvictQueue() {
        return QueueBuilder.durable(CACHE_EVICT_QUEUE)
                .deadLetterExchange(ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding cacheEvictBinding() {
        return BindingBuilder.bind(cacheEvictQueue())
                .to(orderDelayExchange())
                .with(CACHE_EVICT_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return ExchangeBuilder.directExchange(ORDER_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue orderDlxQueue() {
        return QueueBuilder.durable(ORDER_DLX_QUEUE).build();
    }

    @Bean
    public Binding orderDlxBinding() {
        return BindingBuilder.bind(orderDlxQueue())
                .to(orderDlxExchange())
                .with(ORDER_DLX_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
