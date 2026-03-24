package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.utils.constants.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductBloomFilterService {
    private static final long EXPECTED_INSERTIONS = 200_000L;
    private static final double FALSE_PROBABILITY = 0.01D;

    private final RedissonClient redissonClient;
    private final IProductService productService;

    public boolean mightContain(Long productId) {
        if (productId == null || productId <= 0) {
            return false;
        }
        return getBloomFilter().contains(productId);
    }

    public void add(Long productId) {
        if (productId == null || productId <= 0) {
            return;
        }
        getBloomFilter().add(productId);
    }

    public int rebuild() {
        RBloomFilter<Long> bloomFilter = getBloomFilter();
        bloomFilter.delete();
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        List<Long> productIds = productService.lambdaQuery()
                .select(Product::getId)
                .list()
                .stream()
                .map(Product::getId)
                .toList();
        for (Long productId : productIds) {
            bloomFilter.add(productId);
        }
        log.info("Product bloom filter rebuild finished, productCount={}", productIds.size());
        return productIds.size();
    }

    private RBloomFilter<Long> getBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstants.BLOOM_PRODUCT_KEY);
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        return bloomFilter;
    }
}
