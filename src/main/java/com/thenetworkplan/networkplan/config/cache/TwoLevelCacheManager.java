package com.thenetworkplan.networkplan.config.cache;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Pairs each named Caffeine cache with the Redis cache of the same name. */
public class TwoLevelCacheManager implements CacheManager {

    private final CacheManager level1;
    private final CacheManager level2;
    private final Set<String> knownNames;
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    public TwoLevelCacheManager(CacheManager level1, CacheManager level2, Set<String> knownNames) {
        this.level1 = level1;
        this.level2 = level2;
        this.knownNames = knownNames;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::build);
    }

    @Override
    public Collection<String> getCacheNames() {
        return knownNames;
    }

    private Cache build(String name) {
        Cache first = level1.getCache(name);
        if (first == null) {
            throw new IllegalStateException("No L1 cache could be created for '" + name + "'");
        }
        Cache second = null;
        if (level2 != null) {
            try {
                second = level2.getCache(name);
            } catch (RuntimeException ex) {
                second = null;
            }
        }
        return new TwoLevelCache(name, first, second);
    }
}
