package com.example.demo.mq;

import com.example.demo.config.MQConfig;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.PayCheckService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderPayCheckConsumer {
    @Resource
    private PayCheckService payCheckService;

    @RabbitListener(queues = MQConfig.ORDER_PAY_CHECK_QUEUE)
    public void onMessage(OrderPayCheckMessage msg, Message message, Channel channel) throws Exception{
        long tag=message.getMessageProperties().getDeliveryTag();
        try{
            payCheckService.handlePayCheck(msg);
            channel.basicAck(tag,false);
        } catch (BusinessException e) {
            log.error("业务异常，进入死信或后续人工补偿，msg={}", msg, e);
            channel.basicReject(tag,false);
        }catch (Exception e){
            log.error("系统异常，进入死信或后续人工补偿，msg={}", msg, e);
            channel.basicNack(tag, false, false);
        }
    }
}
