package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.entity.Voucher;

public interface IVoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);
    void addVoucher(Voucher voucher);
    void addSeckillVoucher(Voucher voucher);
}
