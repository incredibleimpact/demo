package com.example.demo.utils.cache;

import com.example.demo.utils.constants.RedisConstants;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class CacheOptions {
    public enum RedisExpireMode {
        FIXED,
        RANDOM,
        LOGICAL,
        FOREVER
    }

    public enum RebuildMode {
        DIRECT,
        MUTEX
    }

    private final boolean cacheEnabled;
    private final boolean cacheNullValue;
    private final boolean localCacheEnabled;
    private final RedisExpireMode redisExpireMode;
    private final RebuildMode rebuildMode;
    private final Duration redisTtl;
    private final Duration nullValueTtl;
    private final Duration localCacheTtl;
    private final int randomBound;
    private final TimeUnit randomUnit;
    private final String lockPrefix;
    private final Predicate<Object> existenceChecker;

    private CacheOptions(Builder builder) {
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheNullValue = builder.cacheNullValue;
        this.localCacheEnabled = builder.localCacheEnabled;
        this.redisExpireMode = builder.redisExpireMode;
        this.rebuildMode = builder.rebuildMode;
        this.redisTtl = builder.redisTtl;
        this.nullValueTtl = builder.nullValueTtl;
        this.localCacheTtl = builder.localCacheTtl;
        this.randomBound = builder.randomBound;
        this.randomUnit = builder.randomUnit;
        this.lockPrefix = builder.lockPrefix;
        this.existenceChecker = builder.existenceChecker;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public boolean isCacheNullValue() {
        return cacheNullValue;
    }

    public boolean isLocalCacheEnabled() {
        return localCacheEnabled;
    }

    public RedisExpireMode getRedisExpireMode() {
        return redisExpireMode;
    }

    public RebuildMode getRebuildMode() {
        return rebuildMode;
    }

    public Duration getRedisTtl() {
        return redisTtl;
    }

    public Duration getNullValueTtl() {
        return nullValueTtl;
    }

    public Duration getLocalCacheTtl() {
        return localCacheTtl;
    }

    public int getRandomBound() {
        return randomBound;
    }

    public TimeUnit getRandomUnit() {
        return randomUnit;
    }

    public String getLockPrefix() {
        return lockPrefix;
    }

    public Predicate<Object> getExistenceChecker() {
        return existenceChecker;
    }

    public static final class Builder {
        private boolean cacheEnabled = true;
        private boolean cacheNullValue = true;
        private boolean localCacheEnabled = true;
        private RedisExpireMode redisExpireMode = RedisExpireMode.FIXED;
        private RebuildMode rebuildMode = RebuildMode.DIRECT;
        private Duration redisTtl = Duration.ofMinutes(30);
        private Duration nullValueTtl = Duration.ofMinutes(RedisConstants.CACHE_NULL_TTL);
        private Duration localCacheTtl = Duration.ofSeconds(30);
        private int randomBound;
        private TimeUnit randomUnit = TimeUnit.MINUTES;
        private String lockPrefix = RedisConstants.LOCK_SHOP_KEY;
        private Predicate<Object> existenceChecker;

        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        public Builder cacheNullValue(boolean cacheNullValue) {
            this.cacheNullValue = cacheNullValue;
            return this;
        }

        public Builder localCacheEnabled(boolean localCacheEnabled) {
            this.localCacheEnabled = localCacheEnabled;
            return this;
        }

        public Builder redisExpireMode(RedisExpireMode redisExpireMode) {
            this.redisExpireMode = redisExpireMode;
            return this;
        }

        public Builder rebuildMode(RebuildMode rebuildMode) {
            this.rebuildMode = rebuildMode;
            return this;
        }

        public Builder redisTtl(long time, TimeUnit unit) {
            this.redisTtl = Duration.ofSeconds(unit.toSeconds(time));
            return this;
        }

        public Builder redisTtl(Duration redisTtl) {
            this.redisTtl = redisTtl;
            return this;
        }

        public Builder nullValueTtl(long time, TimeUnit unit) {
            this.nullValueTtl = Duration.ofSeconds(unit.toSeconds(time));
            return this;
        }

        public Builder nullValueTtl(Duration nullValueTtl) {
            this.nullValueTtl = nullValueTtl;
            return this;
        }

        public Builder localCacheTtl(long time, TimeUnit unit) {
            this.localCacheTtl = Duration.ofSeconds(unit.toSeconds(time));
            return this;
        }

        public Builder localCacheTtl(Duration localCacheTtl) {
            this.localCacheTtl = localCacheTtl;
            return this;
        }

        public Builder randomTtl(int randomBound, TimeUnit randomUnit) {
            this.randomBound = randomBound;
            this.randomUnit = randomUnit;
            return this;
        }

        public Builder lockPrefix(String lockPrefix) {
            this.lockPrefix = lockPrefix;
            return this;
        }

        public Builder existenceChecker(Predicate<Object> existenceChecker) {
            this.existenceChecker = existenceChecker;
            return this;
        }

        public CacheOptions build() {
            return new CacheOptions(this);
        }
    }
}
