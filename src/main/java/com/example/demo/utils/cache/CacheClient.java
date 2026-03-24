package com.example.demo.utils.cache;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.demo.exception.CacheDegradeException;
import com.example.demo.mq.CacheEvictMessage;
import com.example.demo.mq.ReliableMessageSender;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.UUID;

@Component
public class CacheClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private static final String CACHE_DEGRADE_MSG = "CACHE_DB_FALLBACK_DEGRADED";

    private final StringRedisTemplate stringRedisTemplate;
    private final LocalCacheClient localCacheClient;
    private final RedissonClient redissonClient;
    private final ReliableMessageSender reliableMessageSender;
    @Value("${app.cache.db-fallback-max-concurrency:20}")
    private int dbFallbackMaxConcurrency;
    private Semaphore dbFallbackSemaphore;

    public CacheClient(StringRedisTemplate stringRedisTemplate,
                       LocalCacheClient localCacheClient,
                       RedissonClient redissonClient,
                       ReliableMessageSender reliableMessageSender) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.localCacheClient = localCacheClient;
        this.redissonClient = redissonClient;
        this.reliableMessageSender = reliableMessageSender;
    }

    @PostConstruct
    public void init() {
        this.dbFallbackSemaphore = new Semaphore(Math.max(1, dbFallbackMaxConcurrency), true);
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        writeCache(key, value, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.FIXED)
                .redisTtl(time, unit)
                .localCacheTtl(resolveLocalTtl(Duration.ofSeconds(unit.toSeconds(time))))
                .build());
    }

    public void setWithRandomTtl(String key, Object value, Long time, TimeUnit unit, int randomBound, TimeUnit randomUnit) {
        writeCache(key, value, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.RANDOM)
                .redisTtl(time, unit)
                .randomTtl(randomBound, randomUnit)
                .localCacheTtl(resolveLocalTtl(Duration.ofSeconds(unit.toSeconds(time))))
                .build());
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        writeCache(key, value, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.LOGICAL)
                .redisTtl(time, unit)
                .localCacheTtl(resolveLocalTtl(Duration.ofSeconds(unit.toSeconds(time))))
                .build());
    }

    public void setForever(String key, Object value) {
        writeCache(key, value, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.FOREVER)
                .localCacheTtl(Duration.ofHours(1))
                .build());
    }

    public <R, ID> R query(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, CacheOptions options) {
        String key = keyPrefix + id;
        return queryInternal(key, id, singleValueAdapter(type), dbFallback, options);
    }

    public <R, ID> List<R> queryList(
            String keyPrefix, ID id, Class<R> elementType, Function<ID, List<R>> dbFallback, CacheOptions options) {
        String key = keyPrefix + id;
        return queryInternal(key, id, listValueAdapter(elementType), dbFallback, options);
    }

    public <R, ID> R queryWithPassThroughAndLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        return query(keyPrefix, id, type, dbFallback, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.LOGICAL)
                .rebuildMode(CacheOptions.RebuildMode.MUTEX)
                .redisTtl(time, unit)
                .localCacheTtl(resolveLocalTtl(Duration.ofSeconds(unit.toSeconds(time))))
                .build());
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit,
            int randomBound, TimeUnit randomUnit) {
        return query(keyPrefix, id, type, dbFallback, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.RANDOM)
                .rebuildMode(CacheOptions.RebuildMode.DIRECT)
                .redisTtl(time, unit)
                .randomTtl(randomBound, randomUnit)
                .localCacheTtl(resolveLocalTtl(Duration.ofSeconds(unit.toSeconds(time))))
                .build());
    }

    public <R, ID> List<R> queryListWithPassThroughAndLogicalExpire(
            String keyPrefix, ID id, Class<R> elementType, Function<ID, List<R>> dbFallback, Long time, TimeUnit unit) {
        return queryList(keyPrefix, id, elementType, dbFallback, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.LOGICAL)
                .rebuildMode(CacheOptions.RebuildMode.MUTEX)
                .redisTtl(time, unit)
                .localCacheTtl(resolveLocalTtl(Duration.ofSeconds(unit.toSeconds(time))))
                .build());
    }

    public <R, ID> List<R> queryListWithMutex(
            String keyPrefix, ID id, Class<R> elementType, Function<ID, List<R>> dbFallback, Long time, TimeUnit unit,
            int randomBound, TimeUnit randomUnit) {
        return queryList(keyPrefix, id, elementType, dbFallback, CacheOptions.builder()
                .redisExpireMode(CacheOptions.RedisExpireMode.RANDOM)
                .rebuildMode(CacheOptions.RebuildMode.MUTEX)
                .redisTtl(time, unit)
                .randomTtl(randomBound, randomUnit)
                .localCacheTtl(resolveLocalTtl(Duration.ofSeconds(unit.toSeconds(time))))
                .build());
    }

    public void delete(String key) {
        localCacheClient.evict(key);
        stringRedisTemplate.delete(key);
    }

    public void deleteWithDelay(String key, long delayMillis) {
        reliableMessageSender.sendCacheEvict(new CacheEvictMessage(UUID.randomUUID().toString(), key), delayMillis);
    }

    // 统一缓存查询模板：先本地，再 Redis，最后按策略回源 DB。
    private <V, ID> V queryInternal(
            String key, ID id, CacheValueAdapter<V> adapter, Function<ID, V> dbFallback, CacheOptions options) {
        if (!options.isCacheEnabled()) {
            return dbFallback.apply(id);
        }
        if (blockedByExistenceChecker(id, options)) {
            return adapter.emptyValue();
        }

        V localValue = readFromLocal(key, adapter, options);
        if (localValue != null) {
            return localValue;
        }
        if (localCacheClient.isNullMarker(key)) {
            return adapter.emptyValue();
        }

        RedisLookupResult<V> redisLookup = readFromRedis(key, adapter, options);
        if (redisLookup.hit()) {
            if (redisLookup.nullValue()) {
                cacheNullInLocal(key, options);
                return adapter.emptyValue();
            }
            cacheValueInLocal(key, redisLookup.value(), options);
            if (!redisLookup.expired()) {
                return redisLookup.value();
            }
            rebuildAsync(key, id, adapter, dbFallback, options);
            return redisLookup.value();
        }

        if (options.getRebuildMode() == CacheOptions.RebuildMode.MUTEX) {
            return loadWithMutex(key, id, adapter, dbFallback, options);
        }
        return loadDirectly(key, id, adapter, dbFallback, options);
    }

    @SuppressWarnings("unchecked")
    // existenceChecker 只是一个接口扩展点，调用方可以接布隆过滤器、白名单或其他快速存在性判断。
    private <ID> boolean blockedByExistenceChecker(ID id, CacheOptions options) {
        if (options.getExistenceChecker() == null) {
            return false;
        }
        Predicate<ID> checker = (Predicate<ID>) options.getExistenceChecker();
        return !checker.test(id);
    }

    private <V> V readFromLocal(String key, CacheValueAdapter<V> adapter, CacheOptions options) {
        if (!options.isLocalCacheEnabled()) {
            return null;
        }
        LocalCacheClient.LookupResult localLookup = localCacheClient.lookup(key);
        if (!localLookup.isHit()) {
            return null;
        }
        if (localLookup.isNullMarker()) {
            return adapter.emptyValue();
        }
        return adapter.fromLocal(localLookup.getRawValue());
    }

    // 逻辑过期模式下，Redis 即使“过期”也会先返回旧值，再异步重建。
    private <V> RedisLookupResult<V> readFromRedis(String key, CacheValueAdapter<V> adapter, CacheOptions options) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return RedisLookupResult.miss();
        }
        if (StrUtil.isBlank(json)) {
            return RedisLookupResult.nullMarkerHit();
        }
        if (options.getRedisExpireMode() == CacheOptions.RedisExpireMode.LOGICAL) {
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            V value = adapter.fromLogicalRedis(redisData.getData());
            boolean expired = !redisData.getExpireTime().isAfter(LocalDateTime.now());
            return RedisLookupResult.hit(value, expired);
        }
        return RedisLookupResult.hit(adapter.fromRedis(json), false);
    }

    // DIRECT 模式适合普通缓存穿透/雪崩防护场景，回源并发过高时直接降级。
    private <V, ID> V loadDirectly(
            String key, ID id, CacheValueAdapter<V> adapter, Function<ID, V> dbFallback, CacheOptions options) {
        boolean acquired = dbFallbackSemaphore.tryAcquire();
        if (!acquired) {
            throw new CacheDegradeException(CACHE_DEGRADE_MSG);
        }
        try {
            RedisLookupResult<V> redisLookup = readFromRedis(key, adapter, options);
            if (redisLookup.hit()) {
                if (redisLookup.nullValue()) {
                    cacheNullInLocal(key, options);
                    return adapter.emptyValue();
                }
                cacheValueInLocal(key, redisLookup.value(), options);
                return redisLookup.value();
            }

            V value = dbFallback.apply(id);
            if (adapter.shouldCacheAsNull(value)) {
                cacheNullValue(key, options);
                return adapter.emptyValue();
            }
            writeCache(key, value, options);
            return value;
        } finally {
            dbFallbackSemaphore.release();
        }
    }

    // MUTEX 模式适合热点 key，确保同一时间只有一个线程真正回源重建缓存。
    private <V, ID> V loadWithMutex(
            String key, ID id, CacheValueAdapter<V> adapter, Function<ID, V> dbFallback, CacheOptions options) {
        String lockKey = options.getLockPrefix() + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = tryLock(lock);
        if (!locked) {
            sleepBriefly();
            return loadWithMutex(key, id, adapter, dbFallback, options);
        }

        try {
            RedisLookupResult<V> redisLookup = readFromRedis(key, adapter, options);
            if (redisLookup.hit()) {
                if (redisLookup.nullValue()) {
                    cacheNullInLocal(key, options);
                    return adapter.emptyValue();
                }
                cacheValueInLocal(key, redisLookup.value(), options);
                return redisLookup.value();
            }

            V value = dbFallback.apply(id);
            if (adapter.shouldCacheAsNull(value)) {
                cacheNullValue(key, options);
                return adapter.emptyValue();
            }
            writeCache(key, value, options);
            return value;
        } finally {
            unlock(lock);
        }
    }

    // 热点缓存逻辑过期后，后台异步重建，前台请求继续读旧值，避免瞬时打爆 DB。
    private <V, ID> void rebuildAsync(
            String key, ID id, CacheValueAdapter<V> adapter, Function<ID, V> dbFallback, CacheOptions options) {
        String lockKey = options.getLockPrefix() + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = tryLock(lock);
        if (!locked) {
            return;
        }
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                V newValue = dbFallback.apply(id);
                if (adapter.shouldCacheAsNull(newValue)) {
                    cacheNullValue(key, options);
                    return;
                }
                writeCache(key, newValue, options);
            } finally {
                unlock(lock);
            }
        });
    }

    // Redis 写入策略由 CacheOptions 驱动，业务层只需要描述场景，不再关心具体写法。
    private void writeCache(String key, Object value, CacheOptions options) {
        switch (options.getRedisExpireMode()) {
            case LOGICAL:
                writeLogicalExpire(key, value, options.getRedisTtl());
                break;
            case RANDOM:
                writeRandomTtl(key, value, options.getRedisTtl(), options.getRandomBound(), options.getRandomUnit());
                break;
            case FOREVER:
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
                break;
            case FIXED:
                writeFixedTtl(key, value, options.getRedisTtl());
                break;
            default:
                throw new IllegalStateException("Unsupported redis expire mode");
        }
        cacheValueInLocal(key, value, options);
    }

    private void writeFixedTtl(String key, Object value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), toSeconds(ttl), TimeUnit.SECONDS);
    }

    private void writeRandomTtl(String key, Object value, Duration ttl, int randomBound, TimeUnit randomUnit) {
        long baseSeconds = toSeconds(ttl);
        long randomSeconds = randomBound <= 0 ? 0 : RandomUtil.randomLong(randomBound + 1L) * randomUnit.toSeconds(1);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), Math.max(1, baseSeconds + randomSeconds), TimeUnit.SECONDS);
    }

    private void writeLogicalExpire(String key, Object value, Duration ttl) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(toSeconds(ttl)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    private void cacheNullValue(String key, CacheOptions options) {
        if (!options.isCacheNullValue()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key, "", toSeconds(options.getNullValueTtl()), TimeUnit.SECONDS);
        cacheNullInLocal(key, options);
    }

    private void cacheValueInLocal(String key, Object value, CacheOptions options) {
        if (!options.isLocalCacheEnabled()) {
            return;
        }
        if (options.getRedisExpireMode() == CacheOptions.RedisExpireMode.FOREVER) {
            localCacheClient.putForever(key, value);
            return;
        }
        localCacheClient.put(key, value, options.getLocalCacheTtl());
    }

    private void cacheNullInLocal(String key, CacheOptions options) {
        if (!options.isLocalCacheEnabled() || !options.isCacheNullValue()) {
            return;
        }
        localCacheClient.putNull(key, options.getLocalCacheTtl());
    }

    private Duration resolveLocalTtl(Duration ttl) {
        long seconds = Math.max(1, ttl.toSeconds());
        return Duration.ofSeconds(Math.min(seconds, 30));
    }

    private long toSeconds(Duration duration) {
        return Math.max(1, duration.toSeconds());
    }

    private boolean tryLock(RLock lock) {
        try {
            return lock.tryLock(0, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void unlock(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private <R> CacheValueAdapter<R> singleValueAdapter(Class<R> type) {
        return new CacheValueAdapter<R>() {
            @Override
            public R fromRedis(String json) {
                return JSONUtil.toBean(json, type);
            }

            @Override
            public R fromLogicalRedis(Object data) {
                return JSONUtil.toBean((JSONObject) data, type);
            }

            @Override
            public R fromLocal(Object value) {
                return type.cast(value);
            }

            @Override
            public boolean shouldCacheAsNull(R value) {
                return value == null;
            }

            @Override
            public R emptyValue() {
                return null;
            }
        };
    }

    private <R> CacheValueAdapter<List<R>> listValueAdapter(Class<R> elementType) {
        return new CacheValueAdapter<List<R>>() {
            @Override
            public List<R> fromRedis(String json) {
                return JSONUtil.toList(JSONUtil.parseArray(json), elementType);
            }

            @Override
            public List<R> fromLogicalRedis(Object data) {
                return JSONUtil.toList(JSONUtil.parseArray(data), elementType);
            }

            @Override
            @SuppressWarnings("unchecked")
            public List<R> fromLocal(Object value) {
                return (List<R>) value;
            }

            @Override
            public boolean shouldCacheAsNull(List<R> value) {
                return value == null || value.isEmpty();
            }

            @Override
            public List<R> emptyValue() {
                return Collections.emptyList();
            }
        };
    }

    // 通过适配器屏蔽“单对象”和“列表对象”的序列化/空值语义差异。
    private interface CacheValueAdapter<V> {
        V fromRedis(String json);

        V fromLogicalRedis(Object data);

        V fromLocal(Object value);

        boolean shouldCacheAsNull(V value);

        V emptyValue();
    }

    private static final class RedisLookupResult<V> {
        private final boolean hit;
        private final boolean nullValue;
        private final boolean expired;
        private final V value;

        private RedisLookupResult(boolean hit, boolean nullValue, boolean expired, V value) {
            this.hit = hit;
            this.nullValue = nullValue;
            this.expired = expired;
            this.value = value;
        }

        private boolean hit() {
            return hit;
        }

        private boolean nullValue() {
            return nullValue;
        }

        private boolean expired() {
            return expired;
        }

        private V value() {
            return value;
        }

        private static <V> RedisLookupResult<V> miss() {
            return new RedisLookupResult<>(false, false, false, null);
        }

        private static <V> RedisLookupResult<V> nullMarkerHit() {
            return new RedisLookupResult<>(true, true, false, null);
        }

        private static <V> RedisLookupResult<V> hit(V value, boolean expired) {
            return new RedisLookupResult<>(true, false, expired, value);
        }
    }
}
