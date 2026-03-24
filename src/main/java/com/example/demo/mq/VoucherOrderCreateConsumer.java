package com.example.demo.mq;

import com.example.demo.config.MQConfig;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VoucherOrderCreateConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = MQConfig.ORDER_CREATE_QUEUE)
    public void onMessage(VoucherOrderCreateMessage msg,
                          Message message,
                          Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            voucherOrderService.handleCrateVoucherOrder(msg);
            //tag是要确认的那条消息的投递标识,false表示只确认当前这一条消息而不批量确认之前未ack的消息
            channel.basicAck(tag, false);
        } catch (BusinessException e) {
            log.error("业务异常，进入死信或后续人工补偿，msg={}", msg, e);
            channel.basicReject(tag, false);
        } catch (Exception e) {
            log.error("系统异常，进入死信或后续人工补偿，msg={}", msg, e);
            channel.basicNack(tag, false, false);//第三个参数表示是否重新入队
        }
    }
}
