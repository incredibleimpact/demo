package com.example.demo.utils.warmup;

import com.example.demo.service.SeckillRedisService;
import com.example.demo.service.ProductBloomFilterService;
import com.example.demo.service.ShopBloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationWarmUp {
    private final ShopBloomFilterService shopBloomFilterService;
    private final ProductBloomFilterService productBloomFilterService;
    private final SeckillRedisService seckillRedisService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        int shopCount = shopBloomFilterService.rebuild();
        int productCount = productBloomFilterService.rebuild();
        seckillRedisService.warmUp();
        log.info("Application warm-up finished, shopBloomCount={}, productBloomCount={}", shopCount, productCount);
    }
}
