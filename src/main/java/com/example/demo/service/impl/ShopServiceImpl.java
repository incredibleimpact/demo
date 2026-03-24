package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.Result;
import com.example.demo.entity.Shop;
import com.example.demo.mapper.ShopMapper;
import com.example.demo.service.IShopService;
import com.example.demo.service.ShopBloomFilterService;
import com.example.demo.utils.cache.CacheClient;
import com.example.demo.utils.cache.CacheOptions;
import com.example.demo.utils.constants.MQConstants;
import com.example.demo.utils.constants.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.TimeUnit;

import static com.example.demo.utils.constants.RedisConstants.CACHE_SHOP_KEY;
import static com.example.demo.utils.constants.RedisConstants.CACHE_SHOP_TTL;

@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopBloomFilterService shopBloomFilter;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.query(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                CacheOptions.builder()
                        .redisExpireMode(CacheOptions.RedisExpireMode.LOGICAL)
                        .rebuildMode(CacheOptions.RebuildMode.MUTEX)
                        .redisTtl(CACHE_SHOP_TTL, TimeUnit.MINUTES)
                        .localCacheTtl(30, TimeUnit.SECONDS)
                        .lockPrefix(RedisConstants.LOCK_SHOP_KEY)
                        .existenceChecker(shopId -> shopBloomFilter.mightContain((Long) shopId))
                        .build()
        );

        if (shop == null) {
            return Result.fail("shop not found");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("shop id is required");
        }
        super.updateById(shop);
        try {
            cacheClient.delete(CACHE_SHOP_KEY + id);
        } catch (Exception e) {
            log.error("shop cache delete failed immediately, shopId={}", id, e);
        }
        Runnable sendDelayedEvict = () -> {
            try {
                cacheClient.deleteWithDelay(CACHE_SHOP_KEY + id, MQConstants.SHOP_CACHE_EVICT_DELAY_MILLIS);
            } catch (Exception e) {
                log.error("shop cache delayed evict message send failed, shopId={}", id, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendDelayedEvict.run();
                }
            });
        } else {
            sendDelayedEvict.run();
        }
        return Result.ok();
    }

    @Override
    public boolean save(Shop entity) {
        boolean success = super.save(entity);
        if (success) {
            shopBloomFilter.add(entity.getId());
        }
        return success;
    }
}
