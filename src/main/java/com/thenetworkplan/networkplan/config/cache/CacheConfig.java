package com.thenetworkplan.networkplan.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Two-level cache: Caffeine (L1, in-process) in front of Redis (L2, shared).
 *
 * <p>What is cached is reference data and read models, never a command result:
 * airports and aircraft types (AIP / AFM revisions), the fleet with its
 * airworthiness status, and the rendered dispatch board. Operational writes evict
 * the caches they invalidate, so the board can never show a stale AOG.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfig.class);

    private static final Set<String> CACHE_NAMES = Set.of(
            CacheNames.AIRPORTS,
            CacheNames.AIRCRAFT_TYPES,
            CacheNames.FLEET,
            CacheNames.DISPATCH_BOARD);

    @Bean
    public CaffeineCacheManager level1CacheManager(NetplusCacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(properties.getL1MaximumSize())
                .expireAfterWrite(properties.getL1Ttl())
                .recordStats());
        manager.setAllowNullValues(false);
        return manager;
    }

    @Bean
    public RedisCacheManager level2CacheManager(RedisConnectionFactory connectionFactory,
                                               NetplusCacheProperties properties) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getL2Ttl())
                .prefixCacheNameWith(properties.effectiveKeyPrefix())
                .disableCachingNullValues()
                // JDK serialisation rather than JSON: every cached DTO is a
                // Serializable record carrying java.time values, and JDK
                // serialisation round-trips them without depending on which
                // Jackson modules happen to be registered. Redis contents are
                // then opaque to redis-cli, which is an acceptable trade for a
                // cache that must never hand back a half-deserialised value.
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.java()));

        Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
        properties.getTtlOverrides().forEach((name, ttl) -> perCache.put(name, base.entryTtl(ttl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Bean
    @Primary
    public CacheManager cacheManager(CaffeineCacheManager level1,
                                     RedisCacheManager level2,
                                     NetplusCacheProperties properties) {
        if (!properties.isEnabled()) {
            LOG.info("netplus.cache.enabled=false — L1 Caffeine only, Redis is not used");
            return new TwoLevelCacheManager(level1, null, CACHE_NAMES);
        }
        LOG.info("Cache: L1 Caffeine ({} entries / {}) in front of L2 Redis ({}, prefix '{}')",
                properties.getL1MaximumSize(), properties.getL1Ttl(),
                properties.getL2Ttl(), properties.effectiveKeyPrefix());
        return new TwoLevelCacheManager(level1, level2, CACHE_NAMES);
    }
}
