package com.example.demo.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.ProductSearchResult;
import com.example.demo.dto.Result;
import com.example.demo.entity.Product;
import com.example.demo.exception.CacheDegradeException;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.service.IProductService;
import com.example.demo.service.ProductBloomFilterService;
import com.example.demo.utils.cache.CacheClient;
import com.example.demo.utils.cache.CacheOptions;
import com.example.demo.utils.constants.MQConstants;
import com.example.demo.utils.constants.RedisConstants;
import com.example.demo.utils.constants.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {
    @Resource
    private CacheClient cacheClient;
    @Resource
    private ProductBloomFilterService productBloomFilterService;

    @Override
    public Result queryById(Long id) {
        Product product = cacheClient.query(
                RedisConstants.CACHE_PRODUCT_KEY,
                id,
                Product.class,
                this::getById,
                CacheOptions.builder()
                        .redisExpireMode(CacheOptions.RedisExpireMode.LOGICAL)
                        .rebuildMode(CacheOptions.RebuildMode.MUTEX)
                        .redisTtl(RedisConstants.CACHE_PRODUCT_TTL, TimeUnit.MINUTES)
                        .localCacheTtl(30, TimeUnit.SECONDS)
                        .lockPrefix(RedisConstants.LOCK_PRODUCT_KEY)
                        .existenceChecker(productId -> productBloomFilterService.mightContain((Long) productId))
                        .build()
        );
        if (product == null) {
            return Result.fail("product not found");
        }
        return Result.ok(product);
    }

    @Override
    public Result search(String keyword, Long categoryId, Integer current, Integer pageSize) {
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null || pageSize < 1 ? SystemConstants.DEFAULT_PAGE_SIZE : Math.min(pageSize, SystemConstants.MAX_PAGE_SIZE);
        String cacheId = buildSearchCacheId(keyword, categoryId, safeCurrent, safePageSize);
        try {
            ProductSearchResult result = cacheClient.query(
                    RedisConstants.CACHE_PRODUCT_SEARCH_KEY,
                    cacheId,
                    ProductSearchResult.class,
                    ignored -> searchFromDb(keyword, categoryId, safeCurrent, safePageSize),
                    CacheOptions.builder()
                            .redisExpireMode(CacheOptions.RedisExpireMode.RANDOM)
                            .rebuildMode(CacheOptions.RebuildMode.DIRECT)
                            .redisTtl(5, TimeUnit.MINUTES)
                            .randomTtl(5, TimeUnit.MINUTES)
                            .localCacheTtl(30, TimeUnit.SECONDS)
                            .build()
            );
            if (result == null) {
                return Result.ok(Collections.emptyList(), 0L);
            }
            return Result.ok(result.getRecords(), result.getTotal());
        } catch (CacheDegradeException e) {
            return Result.fail("system busy, please retry later");
        }
    }

    @Override
    @Transactional
    public Result create(Product product) {
        save(product);
        productBloomFilterService.add(product.getId());
        return Result.ok(product.getId());
    }

    @Override
    @Transactional
    public Result updateProduct(Product product) {
        if (product.getId() == null) {
            return Result.fail("product id is required");
        }
        updateById(product);
        evictDetailCache(product.getId());
        return Result.ok();
    }

    private ProductSearchResult searchFromDb(String keyword, Long categoryId, int current, int pageSize) {
        Page<Product> page = lambdaQuery()
                .like(StrUtil.isNotBlank(keyword), Product::getName, keyword)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(Product::getStatus, 1)
                .page(new Page<>(current, pageSize));
        return new ProductSearchResult(page.getRecords(), page.getTotal());
    }

    private String buildSearchCacheId(String keyword, Long categoryId, int current, int pageSize) {
        String normalizedKeyword = StrUtil.blankToDefault(StrUtil.trim(keyword), "_");
        String normalizedCategoryId = categoryId == null ? "_" : categoryId.toString();
        return normalizedKeyword + ":" + normalizedCategoryId + ":" + current + ":" + pageSize;
    }

    private void evictDetailCache(Long productId) {
        String cacheKey = RedisConstants.CACHE_PRODUCT_KEY + productId;
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
