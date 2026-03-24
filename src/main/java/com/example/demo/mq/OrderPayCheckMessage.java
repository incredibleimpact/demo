package com.example.demo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPayCheckMessage implements Serializable {
    private String msgId;
    private Long orderId;
    private Integer checkIndex;   // 第几次检查
}
