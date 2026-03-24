package com.example.demo.service;

import com.example.demo.entity.Shop;
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
public class ShopBloomFilterService {
    private static final long EXPECTED_INSERTIONS = 100_000L;
    private static final double FALSE_PROBABILITY = 0.01D;

    private final RedissonClient redissonClient;
    private final IShopService shopService;

    public boolean mightContain(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return false;
        }
        return getBloomFilter().contains(shopId);
    }

    public void add(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return;
        }
        getBloomFilter().add(shopId);
    }

    public int rebuild() {
        RBloomFilter<Long> bloomFilter = getBloomFilter();
        bloomFilter.delete();
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);

        List<Long> shopIds = shopService.lambdaQuery()
                .select(Shop::getId)
                .list()
                .stream()
                .map(Shop::getId)
                .toList();

        for (Long shopId : shopIds) {
            bloomFilter.add(shopId);
        }
        log.info("Shop bloom filter rebuild finished, shopCount={}", shopIds.size());
        return shopIds.size();
    }

    private RBloomFilter<Long> getBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstants.BLOOM_SHOP_KEY);
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        return bloomFilter;
    }
}
