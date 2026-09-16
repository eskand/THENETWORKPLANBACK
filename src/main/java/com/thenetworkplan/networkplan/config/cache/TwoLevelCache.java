package com.thenetworkplan.networkplan.config.cache;

import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * L1 Caffeine in front of L2 Redis.
 *
 * <p>Reads hit Caffeine first (nanoseconds, no network); a miss falls through to
 * Redis and, when Redis answers, the value is promoted into L1. Writes and
 * evictions go to both, so a second application instance never serves a value
 * this one has just invalidated.
 *
 * <p>Redis is treated as optional: if it is down, every L2 call is swallowed and
 * the cache degrades to L1 only rather than failing the request. That matters in
 * development, where Redis is often not running.
 */
public class TwoLevelCache implements Cache {

    private static final Logger LOG = LoggerFactory.getLogger(TwoLevelCache.class);

    private final String name;
    private final Cache level1;
    private final Cache level2;

    public TwoLevelCache(String name, Cache level1, Cache level2) {
        this.name = name;
        this.level1 = level1;
        this.level2 = level2;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return level1.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper fromL1 = level1.get(key);
        if (fromL1 != null) {
            return fromL1;
        }
        ValueWrapper fromL2 = readLevel2(key);
        if (fromL2 != null) {
            level1.put(key, fromL2.get());
        }
        return fromL2;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        Object value = wrapper == null ? null : wrapper.get();
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type " + type.getName() + ": " + value);
        }
        return type == null ? castUnchecked(value) : type.cast(value);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return castUnchecked(wrapper.get());
        }
        try {
            T loaded = valueLoader.call();
            put(key, loaded);
            return loaded;
        } catch (Exception ex) {
            throw new ValueRetrievalException(key, valueLoader, ex);
        }
    }

    @Override
    public void put(Object key, Object value) {
        level1.put(key, value);
        if (level2 != null) {
            try {
                level2.put(key, value);
            } catch (RuntimeException ex) {
                LOG.debug("L2 cache '{}' unavailable on put, keeping L1 only: {}", name, ex.getMessage());
            }
        }
    }

    @Override
    public void evict(Object key) {
        level1.evict(key);
        if (level2 != null) {
            try {
                level2.evict(key);
            } catch (RuntimeException ex) {
                LOG.debug("L2 cache '{}' unavailable on evict: {}", name, ex.getMessage());
            }
        }
    }

    @Override
    public void clear() {
        level1.clear();
        if (level2 != null) {
            try {
                level2.clear();
            } catch (RuntimeException ex) {
                LOG.debug("L2 cache '{}' unavailable on clear: {}", name, ex.getMessage());
            }
        }
    }

    private ValueWrapper readLevel2(Object key) {
        if (level2 == null) {
            return null;
        }
        try {
            return level2.get(key);
        } catch (RuntimeException ex) {
            LOG.debug("L2 cache '{}' unavailable on get, serving from L1 only: {}", name, ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T castUnchecked(Object value) {
        return (T) value;
    }
}
