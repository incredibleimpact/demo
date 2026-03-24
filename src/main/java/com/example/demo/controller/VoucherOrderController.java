package com.example.demo.controller;


import com.example.demo.utils.limiter.RateLimit;
import com.example.demo.dto.Result;
import com.example.demo.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @RateLimit(
            globalEnable = true,  globalCount = 1000, globalWindow = 1,
            ipEnable    = true,   ipCount     = 50,   ipWindow    = 1,
            userEnable  = true,   userCount   = 10,   userWindow  = 1,
            message = "下单过于频繁，请1秒后重试"
    )
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
