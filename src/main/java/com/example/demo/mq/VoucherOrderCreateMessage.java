package com.example.demo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherOrderCreateMessage implements Serializable {
    private String msgId;      // 消息唯一ID
    private Long orderId;      // 订单ID，秒杀入口生成
    private Long userId;
    private Long voucherId;
}
