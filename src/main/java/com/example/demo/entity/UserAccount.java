package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_account")
public class UserAccount implements Serializable {
    @TableId(value = "user_id")
    private Long userId;

    private Long balance;
    private Long frozenBalance;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
