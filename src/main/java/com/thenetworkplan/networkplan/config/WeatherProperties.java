package com.thenetworkplan.networkplan.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Where the weather comes from, and how often.
 *
 * <p>The default source is NOAA's public METAR file service: no key, no
 * account, no secret in a source file. The AVWX relay of annexe A4 remains
 * possible — it needs a token, and that token belongs in an environment
 * variable, which is the first thing the audit asked for.
 */
@Component
@ConfigurationProperties(prefix = "netplus.weather")
@Getter
@Setter
public class WeatherProperties {

    /** NOAA, AVWX or NONE. NONE leaves the screens saying "no source connected". */
    private String provider = "NOAA";

    /** Base URL of the NOAA station files. */
    private String noaaBaseUrl = "https://tgftp.nws.noaa.gov/data/observations/metar/stations";

    /**
     * How long a stored observation is considered fresh enough not to refetch.
     * A METAR is issued every half hour; ten minutes keeps the screen current
     * without hammering a public service.
     */
    private Duration refreshAfter = Duration.ofMinutes(10);

    /** Beyond this, the observation is shown as stale rather than as current. */
    private Duration staleAfter = Duration.ofMinutes(90);

    /** Timeout of one station request. */
    private Duration timeout = Duration.ofSeconds(6);

    /** Token for the AVWX relay. Never written in a source or a property file. */
    private String avwxToken;

    /** Base URL of the AVWX relay described in annexe A4. */
    private String avwxBaseUrl = "https://avwx.rest/api";

    /**
     * Visibility at or below which low visibility procedures apply, in metres.
     *
     * <p>The operator's figure, not an industry one: 1500 m is the common
     * European trigger for LVP preparation. It is declared here and read by
     * {@code LowVisibilityRule}; changing it changes the Status Board and
     * nothing else.
     */
    private int lvpVisibilityM = 1500;

    /** Ceiling at or below which low visibility procedures apply, in feet. */
    private int lvpCeilingFt = 300;
}
