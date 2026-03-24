package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.Result;
import com.example.demo.exception.CacheDegradeException;
import com.example.demo.entity.SeckillVoucher;
import com.example.demo.entity.Voucher;
import com.example.demo.mapper.VoucherMapper;
import com.example.demo.service.ISeckillVoucherService;
import com.example.demo.service.IVoucherService;
import com.example.demo.utils.cache.CacheClient;
import com.example.demo.utils.cache.CacheOptions;
import com.example.demo.utils.constants.MQConstants;
import com.example.demo.utils.constants.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers;
        try {
            vouchers = cacheClient.queryList(
                    RedisConstants.CACHE_VOUCHER_LIST_KEY,
                    shopId,
                    Voucher.class,
                    this::listVoucherOfShopFromDb,
                    CacheOptions.builder()
                            .redisExpireMode(CacheOptions.RedisExpireMode.RANDOM)
                            .rebuildMode(CacheOptions.RebuildMode.MUTEX)
                            .redisTtl(RedisConstants.CACHE_VOUCHER_LIST_TTL, TimeUnit.MINUTES)
                            .randomTtl(5, TimeUnit.MINUTES)
                            .localCacheTtl(30, TimeUnit.SECONDS)
                            .lockPrefix(RedisConstants.LOCK_SHOP_KEY)
                            .build()
            );
        } catch (CacheDegradeException e) {
            return Result.fail("system busy, please retry later");
        }
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addVoucher(Voucher voucher) {
        save(voucher);
        evictVoucherListCache(voucher.getShopId());
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        save(voucher);
        SeckillVoucher seckillVoucher=new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY +voucher.getId(),voucher.getStock().toString());
        evictVoucherListCache(voucher.getShopId());
    }

    private List<Voucher> listVoucherOfShopFromDb(Long shopId) {
        return getBaseMapper().queryVoucherOfShop(shopId);
    }

    private void evictVoucherListCache(Long shopId) {
        String cacheKey = RedisConstants.CACHE_VOUCHER_LIST_KEY + shopId;
        cacheClient.delete(cacheKey);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheClient.deleteWithDelay(cacheKey, MQConstants.SHOP_CACHE_EVICT_DELAY_MILLIS);
                }
            });
            return;
        }
        cacheClient.deleteWithDelay(cacheKey, MQConstants.SHOP_CACHE_EVICT_DELAY_MILLIS);
    }
}
