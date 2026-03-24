package com.example.demo.service;

import com.example.demo.entity.SeckillVoucher;
import com.example.demo.entity.VoucherOrder;
import com.example.demo.utils.constants.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillRedisService {

    private final ISeckillVoucherService seckillVoucherService;

    private final IVoucherOrderService voucherOrderService;

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("rollback_seckill.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }
    public Long identifySeckillQualification(Long voucherId, Long userId){
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderUserSetKey = RedisConstants.SECKILL_ORDER_USER_SET_KEY+ voucherId;
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(stockKey, orderUserSetKey),
                userId.toString()
        );
        return result;
    }
    public void rollbackPreDeduct(Long voucherId, Long userId) {
        String stockKey =RedisConstants.SECKILL_STOCK_KEY+voucherId;
        String userSetKey = RedisConstants.SECKILL_ORDER_USER_SET_KEY+voucherId;

        stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                Arrays.asList(stockKey, userSetKey),
                userId.toString()
        );
    }

    public void warmUp() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        for (SeckillVoucher voucher : vouchers) {
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.SECKILL_STOCK_KEY + voucher.getVoucherId(),
                    String.valueOf(voucher.getStock())
            );
        }

        Map<Long, List<String>> userIdsByVoucher = voucherOrderService.list().stream()
                .collect(Collectors.groupingBy(
                        VoucherOrder::getVoucherId,
                        Collectors.mapping(order -> String.valueOf(order.getUserId()), Collectors.toList())
                ));
        for (Map.Entry<Long, List<String>> entry : userIdsByVoucher.entrySet()) {
            String key = RedisConstants.SECKILL_ORDER_USER_SET_KEY + entry.getKey();
            stringRedisTemplate.delete(key);
            if (!entry.getValue().isEmpty()) {
                stringRedisTemplate.opsForSet().add(key, entry.getValue().toArray(String[]::new));
            }
        }
        log.info("Seckill warm-up finished, voucherCount={}, orderSetCount={}", vouchers.size(), userIdsByVoucher.size());
    }
}
