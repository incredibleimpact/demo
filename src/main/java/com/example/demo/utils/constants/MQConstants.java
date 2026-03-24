package com.example.demo.utils.constants;

import java.time.Duration;
import java.util.List;

public class MQConstants {
    public static final List<Long> PAY_CHECK_DELAY_MILLIS = List.of(
            30_000L,
            90_000L,
            180_000L,
            300_000L,
            600_000L,
            900_000L,
            1_500_000L
    );

    public static final Duration ORDER_PAY_TIMEOUT = Duration.ofMinutes(30);
    public static final int PAY_CHECK_REPAIR_BATCH_SIZE = 100;
    public static final long PAY_CHECK_REPAIR_DELAY_MILLIS = 0L;
    public static final long SHOP_CACHE_EVICT_DELAY_MILLIS = 500L;
}
