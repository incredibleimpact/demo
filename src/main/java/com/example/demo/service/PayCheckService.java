package com.example.demo.service;

import com.example.demo.entity.SeckillVoucher;
import com.example.demo.entity.VoucherOrder;
import com.example.demo.mq.OrderPayCheckMessage;
import com.example.demo.mq.ReliableMessageSender;
import com.example.demo.utils.constants.MQConstants;
import com.example.demo.utils.constants.RedisConstants;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayCheckService {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private ReliableMessageSender reliableMessageSender;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private SeckillRedisService seckillRedisService;

    public void handlePayCheck(OrderPayCheckMessage msg) {
        Long orderId = msg.getOrderId();
        VoucherOrder order = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getStatus, 0)
                .one();
        if (order == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Integer currentIndex = msg.getCheckIndex() == null ? 0 : msg.getCheckIndex();
        Integer nextIndex = currentIndex + 1;
        if (!now.isBefore(order.getCreateTime().plus(MQConstants.ORDER_PAY_TIMEOUT))
                || nextIndex >= MQConstants.PAY_CHECK_DELAY_MILLIS.size()) {
            cancelOrder(order);
            return;
        }

        reliableMessageSender.sendPayCheck(
                new OrderPayCheckMessage(UUID.randomUUID().toString(), orderId, nextIndex),
                MQConstants.PAY_CHECK_DELAY_MILLIS.get(nextIndex)
        );
    }

    @Scheduled(fixedDelay = 60000)
    public void repairExpiredPayCheckMessages() {
        LocalDateTime timeoutLine = LocalDateTime.now().minus(MQConstants.ORDER_PAY_TIMEOUT);
        List<VoucherOrder> timeoutOrders = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getStatus, 0)
                .le(VoucherOrder::getCreateTime, timeoutLine)
                .last("limit " + MQConstants.PAY_CHECK_REPAIR_BATCH_SIZE)
                .list();
        if (timeoutOrders.isEmpty()) {
            return;
        }

        for (VoucherOrder order : timeoutOrders) {
            try {
                reliableMessageSender.sendPayCheck(
                        new OrderPayCheckMessage(UUID.randomUUID().toString(), order.getId(), 0),
                        MQConstants.PAY_CHECK_REPAIR_DELAY_MILLIS
                );
            } catch (Exception e) {
                log.error("补发支付检查消息失败, orderId={}", order.getId(), e);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(VoucherOrder order) {
        String lockKey = RedisConstants.ORDER_PAY_LOCK_KEY + order.getId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            boolean update = voucherOrderService.lambdaUpdate()
                    .eq(VoucherOrder::getId, order.getId())
                    .eq(VoucherOrder::getStatus, 0)
                    .set(VoucherOrder::getStatus, 2)
                    .set(VoucherOrder::getUpdateTime, now)
                    .set(VoucherOrder::getCancelTime, now)
                    .update();
            if (!update) {
                return;
            }
            seckillVoucherService.lambdaUpdate()
                    .eq(SeckillVoucher::getVoucherId, order.getVoucherId())
                    .setSql("stock=stock+1")
                    .update();
            seckillRedisService.rollbackPreDeduct(order.getVoucherId(), order.getUserId());
        } finally {
            lock.unlock();
        }
    }
}
