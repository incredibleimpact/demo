package com.example.demo.utils.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;

@Component
public class LocalCacheClient {
    private static final Object NULL_MARKER = new Object();
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    public static final class LookupResult {
        private final boolean hit;
        private final Object value;

        private LookupResult(boolean hit, Object value) {
            this.hit = hit;
            this.value = value;
        }

        public boolean isHit() {
            return hit;
        }

        public boolean isNullMarker() {
            return hit && value == NULL_MARKER;
        }

        public <T> T getValue(Class<T> type) {
            if (!hit || value == NULL_MARKER) {
                return null;
            }
            return type.cast(value);
        }

        public Object getRawValue() {
            if (!hit || value == NULL_MARKER) {
                return null;
            }
            return value;
        }
    }

    private static final class LocalCacheValue {
        private final Object value;
        private final long expireAtMillis;

        private LocalCacheValue(Object value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }

        private static LocalCacheValue withTtl(Object value, Duration ttl) {
            long safeTtlMillis = Math.max(1L, ttl.toMillis());
            return new LocalCacheValue(value, System.currentTimeMillis() + safeTtlMillis);
        }

        private static LocalCacheValue forever(Object value) {
            return new LocalCacheValue(value, Long.MAX_VALUE);
        }

        private boolean expired() {
            return expireAtMillis != Long.MAX_VALUE && System.currentTimeMillis() >= expireAtMillis;
        }
    }

    private final Cache<String, LocalCacheValue> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .build();

    public LookupResult lookup(String key) {
        LocalCacheValue cacheValue = cache.getIfPresent(key);
        if (cacheValue == null) {
            return new LookupResult(false, null);
        }
        if (cacheValue.expired()) {
            cache.invalidate(key);
            return new LookupResult(false, null);
        }
        return new LookupResult(true, cacheValue.value);
    }

    public boolean contains(String key) {
        return lookup(key).isHit();
    }

    public boolean isNullMarker(String key) {
        return lookup(key).isNullMarker();
    }

    public <T> T get(String key, Class<T> type) {
        return lookup(key).getValue(type);
    }

    public Object getRaw(String key) {
        return lookup(key).getRawValue();
    }

    public void put(String key, Object value) {
        put(key, value, DEFAULT_TTL);
    }

    public void put(String key, Object value, Duration ttl) {
        cache.put(key, LocalCacheValue.withTtl(value, ttl));
    }

    public void putForever(String key, Object value) {
        cache.put(key, LocalCacheValue.forever(value));
    }

    public void putNull(String key) {
        putNull(key, DEFAULT_TTL);
    }

    public void putNull(String key, Duration ttl) {
        cache.put(key, LocalCacheValue.withTtl(NULL_MARKER, ttl));
    }

    public void putNullForever(String key) {
        cache.put(key, LocalCacheValue.forever(NULL_MARKER));
    }

    public void evict(String key) {
        cache.invalidate(key);
    }

    public void evictAll(Collection<String> keys) {
        cache.invalidateAll(keys);
    }
}
