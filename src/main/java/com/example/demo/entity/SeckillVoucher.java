package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
@Data
@TableName("seckill_voucher")
public class SeckillVoucher {
    private Long voucherId;
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}