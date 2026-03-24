package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.Result;
import com.example.demo.entity.Category;
import com.example.demo.mapper.CategoryMapper;
import com.example.demo.service.ICategoryService;
import com.example.demo.utils.cache.CacheClient;
import com.example.demo.utils.cache.CacheOptions;
import com.example.demo.utils.constants.MQConstants;
import com.example.demo.utils.constants.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {
    private static final String ROOT_CACHE_ID = "root";

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryTree() {
        List<Category> categories = cacheClient.queryList(
                RedisConstants.CACHE_CATEGORY_TREE_KEY + ":",
                ROOT_CACHE_ID,
                Category.class,
                ignored -> buildTree(),
                CacheOptions.builder()
                        .redisExpireMode(CacheOptions.RedisExpireMode.FOREVER)
                        .rebuildMode(CacheOptions.RebuildMode.DIRECT)
                        .build()
        );
        return Result.ok(categories);
    }

    @Override
    @Transactional
    public Result create(Category category) {
        save(category);
        evictTreeCache();
        return Result.ok(category.getId());
    }

    @Override
    @Transactional
    public Result updateCategory(Category category) {
        if (category.getId() == null) {
            return Result.fail("category id is required");
        }
        updateById(category);
        evictTreeCache();
        return Result.ok();
    }

    private List<Category> buildTree() {
        List<Category> all = lambdaQuery()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getLevel)
                .orderByAsc(Category::getSort)
                .list();
        Map<Long, List<Category>> grouped = all.stream().collect(Collectors.groupingBy(category -> category.getParentId() == null ? 0L : category.getParentId()));
        List<Category> roots = new ArrayList<>(grouped.getOrDefault(0L, List.of()));
        for (Category root : roots) {
            fillChildren(root, grouped);
        }
        return roots;
    }

    private void fillChildren(Category parent, Map<Long, List<Category>> grouped) {
        List<Category> children = new ArrayList<>(grouped.getOrDefault(parent.getId(), List.of()));
        parent.setChildren(children);
        for (Category child : children) {
            fillChildren(child, grouped);
        }
    }

    private void evictTreeCache() {
        String cacheKey = RedisConstants.CACHE_CATEGORY_TREE_KEY + ":" + ROOT_CACHE_ID;
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
