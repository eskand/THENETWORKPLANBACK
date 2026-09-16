package com.thenetworkplan.networkplan.config.cache;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "netplus.cache")
@Getter
@Setter
public class NetplusCacheProperties {

    /** When false, only the in-process L1 cache is used. */
    private boolean enabled = true;

    private long l1MaximumSize = 10_000;

    private Duration l1Ttl = Duration.ofMinutes(2);

    private Duration l2Ttl = Duration.ofMinutes(30);

    private String keyPrefix = "netplus:";

    /**
     * Shape version of the cached read models. Part of every Redis key.
     *
     * <p>L2 uses JDK serialisation, so a cached value only deserialises into
     * the record shape that wrote it. Adding a field to a DTO — as the OCC
     * on-time and "vs yesterday" figures did to {@code DispatchKpiDto} — makes
     * every value already in Redis unreadable, and a redeploy answers with a
     * deserialisation error until the entries expire. Bumping this number
     * moves the new build to a fresh key space; the old entries are simply
     * left to their own TTL.
     *
     * <p><b>Bump it whenever a cached record gains, loses or reorders a
     * component.</b>
     */
    private int shapeVersion = 2;

    /** The prefix actually written to Redis: {@code netplus:v2:}. */
    public String effectiveKeyPrefix() {
        return keyPrefix + "v" + shapeVersion + ":";
    }

    /** Per-cache L2 time to live, e.g. {@code netplus.cache.ttl-overrides.airports=PT12H}. */
    private Map<String, Duration> ttlOverrides = new LinkedHashMap<>();
}
