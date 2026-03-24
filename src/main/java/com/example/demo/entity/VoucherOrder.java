package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("voucher_order")
public class VoucherOrder {
    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer status;

    private String orderNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
}
