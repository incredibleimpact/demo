package com.example.demo.service;

import com.example.demo.entity.VoucherOrder;
import com.example.demo.utils.constants.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class PaymentService {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedissonClient redissonClient;
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(Long orderId){
        String lockKey=RedisConstants.ORDER_PAY_LOCK_KEY+orderId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock=lock.tryLock();
        if(isLock){
            throw new RuntimeException("支付回调获取锁失败");
        }
        try{
            boolean success = voucherOrderService.lambdaUpdate()
                    .eq(VoucherOrder::getId, orderId)
                    .ne(VoucherOrder::getStatus,2)//未支付或已支付都能更新成功,幂等
                    .set(VoucherOrder::getStatus, 1)
                    .set(VoucherOrder::getUpdateTime, LocalDateTime.now())
                    .update();
            if(!success){//已经被关单了
                log.warn("订单已被关闭,请联系管理员退款,orderId={}",orderId);
            }
        }finally {
            lock.unlock();
        }

    }
}
