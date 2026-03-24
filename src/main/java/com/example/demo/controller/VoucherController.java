package com.example.demo.controller;


import com.example.demo.dto.Result;
import com.example.demo.entity.Voucher;
import com.example.demo.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping("/addVoucher")
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.addVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("/addSeckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     * 秒杀券不是单独的券,而是在优惠券的基础之上增加了stock等字段
     * 在优惠券表中查询优惠券,以及根据优惠券id关联秒杀表的优惠券id,因此可以得到优惠券的完整信息(包括秒杀的信息)
     * 它走了联表查询,所以需要使用Mapper.xml编写sql语句
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}
