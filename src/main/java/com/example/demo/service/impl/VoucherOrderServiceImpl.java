package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.Result;
import com.example.demo.entity.SeckillVoucher;
import com.example.demo.entity.VoucherOrder;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.VoucherOrderMapper;
import com.example.demo.mq.OrderPayCheckMessage;
import com.example.demo.mq.ReliableMessageSender;
import com.example.demo.mq.VoucherOrderCreateMessage;
import com.example.demo.service.ISeckillVoucherService;
import com.example.demo.service.IVoucherOrderService;
import com.example.demo.service.SeckillRedisService;
import com.example.demo.utils.constants.MQConstants;
import com.example.demo.utils.idWorker.RedisIDWorker;
import com.example.demo.utils.login.UserHolder;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIDWorker redisIdWorker;
    @Resource
    private ReliableMessageSender reliableMessageSender;
    @Resource
    private SeckillRedisService seckillRedisService;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = seckillRedisService.identifySeckillQualification(voucherId, userId);
        if (result == null) {
            return Result.fail("系统繁忙，请重试");
        }
        if (result == 1) {
            return Result.fail("库存不足");
        }
        if (result == 2) {
            return Result.fail("不可重复下单");
        }

        Long orderId = redisIdWorker.nextId("voucher:order");
        String msgId = UUID.randomUUID().toString();
        VoucherOrderCreateMessage message = new VoucherOrderCreateMessage(msgId, orderId, userId, voucherId);
        try {
            reliableMessageSender.sendOrderCreate(message);
            return Result.ok("下单成功，正在处理中");
        } catch (Exception e) {
            log.error("发送创建订单消息失败, 回滚Redis预扣库存, msg={}", message, e);
            seckillRedisService.rollbackPreDeduct(voucherId, userId);
            return Result.fail("下单人数过多，请稍后重试");
        }
    }

    @Override
    public void handleCrateVoucherOrder(VoucherOrderCreateMessage msg) {
        Long orderId = msg.getOrderId();
        Long userId = msg.getUserId();
        Long voucherId = msg.getVoucherId();

        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        proxy.createVoucherOrder(orderId, userId, voucherId);
        try {
            reliableMessageSender.sendPayCheck(
                    new OrderPayCheckMessage(UUID.randomUUID().toString(), orderId, 0),
                    MQConstants.PAY_CHECK_DELAY_MILLIS.get(0)
            );
        } catch (Exception e) {
            log.error("发送支付检查消息失败, orderId={}", orderId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(Long orderId, Long userId, Long voucherId) {
        if (lambdaQuery()
                .eq(VoucherOrder::getId, orderId)
                .or()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId)
                .count() > 0) {
            seckillRedisService.rollbackPreDeduct(voucherId, userId);
            return;
        }

        boolean success = seckillVoucherService.lambdaUpdate()
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock - 1")
                .update();
        if (!success) {
            seckillRedisService.rollbackPreDeduct(voucherId, userId);
            throw new BusinessException("业务失败:数据库扣减库存失败");
        }

        LocalDateTime time = LocalDateTime.now();
        save(new VoucherOrder().setId(orderId).setUserId(userId).setVoucherId(voucherId).setStatus(0)
                .setCreateTime(time).setUpdateTime(time));
    }
}
