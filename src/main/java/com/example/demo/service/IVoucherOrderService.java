package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.mq.VoucherOrderCreateMessage;
import com.example.demo.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    void handleCrateVoucherOrder(VoucherOrderCreateMessage msg);

    void createVoucherOrder(Long orderId, Long userId, Long voucherId);

//    Result lockAndCreateOrder(Long userId, Long voucherId);

//    void createVoucherOrder(VoucherOrder voucherOrder);
}
