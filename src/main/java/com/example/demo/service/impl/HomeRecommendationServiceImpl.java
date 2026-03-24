package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.Result;
import com.example.demo.entity.HomeRecommendation;
import com.example.demo.mapper.HomeRecommendationMapper;
import com.example.demo.service.IHomeRecommendationService;
import com.example.demo.utils.cache.CacheClient;
import com.example.demo.utils.cache.CacheOptions;
import com.example.demo.utils.constants.MQConstants;
import com.example.demo.utils.constants.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class HomeRecommendationServiceImpl extends ServiceImpl<HomeRecommendationMapper, HomeRecommendation> implements IHomeRecommendationService {
    private static final String ALL_BIZ_TYPE = "ALL";

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryActiveList(String bizType, Integer limit) {
        String normalizedBizType = normalizeBizType(bizType);
        List<HomeRecommendation> all = cacheClient.queryList(
                RedisConstants.CACHE_HOME_RECOMMEND_KEY,
                normalizedBizType,
                HomeRecommendation.class,
                this::queryActiveFromDb,
                CacheOptions.builder()
                        .redisExpireMode(CacheOptions.RedisExpireMode.RANDOM)
                        .rebuildMode(CacheOptions.RebuildMode.DIRECT)
                        .redisTtl(RedisConstants.CACHE_HOME_RECOMMEND_TTL, TimeUnit.MINUTES)
                        .randomTtl(5, TimeUnit.MINUTES)
                        .localCacheTtl(30, TimeUnit.SECONDS)
                        .build()
        );
        int safeLimit = limit == null || limit < 1 ? 10 : limit;
        return Result.ok(all.stream().limit(safeLimit).toList());
    }

    @Override
    @Transactional
    public Result create(HomeRecommendation recommendation) {
        save(recommendation);
        evictRecommendationCache(recommendation.getBizType());
        return Result.ok(recommendation.getId());
    }

    @Override
    @Transactional
    public Result updateRecommendation(HomeRecommendation recommendation) {
        if (recommendation.getId() == null) {
            return Result.fail("recommendation id is required");
        }
        HomeRecommendation before = getById(recommendation.getId());
        updateById(recommendation);
        if (before != null) {
            evictRecommendationCache(before.getBizType());
        }
        evictRecommendationCache(recommendation.getBizType());
        return Result.ok();
    }

    private List<HomeRecommendation> queryActiveFromDb(String bizType) {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery()
                .eq(!ALL_BIZ_TYPE.equals(bizType), HomeRecommendation::getBizType, bizType)
                .eq(HomeRecommendation::getStatus, 1)
                .le(HomeRecommendation::getStartTime, now)
                .ge(HomeRecommendation::getEndTime, now)
                .orderByDesc(HomeRecommendation::getRank)
                .list();
    }

    private void evictRecommendationCache(String bizType) {
        evictOneRecommendationCache(normalizeBizType(bizType));
        evictOneRecommendationCache(ALL_BIZ_TYPE);
    }

    private void evictOneRecommendationCache(String bizType) {
        String cacheKey = RedisConstants.CACHE_HOME_RECOMMEND_KEY + bizType;
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

    private String normalizeBizType(String bizType) {
        return bizType == null || bizType.isBlank() ? ALL_BIZ_TYPE : bizType.trim().toUpperCase();
    }
}
