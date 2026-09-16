package com.thenetworkplan.networkplan.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Where live aircraft positions come from.
 *
 * <p>The default source is the OpenSky Network's public state vector service:
 * no key, no account, no secret in a source file. Credentials raise the rate
 * limit and belong in environment variables, never here — the audited
 * prototype passed a client secret straight from the browser
 * ({@code connectDirect(clientId, clientSecret)}), with its own comment
 * admitting it "exposes secret".
 *
 * <p>{@code provider = NONE} leaves Flight Watch saying "no ADS-B source
 * connected", which is a state, not a failure.
 */
@Component
@ConfigurationProperties(prefix = "netplus.adsb")
@Getter
@Setter
public class AdsbProperties {

    /** OPENSKY or NONE. */
    private String provider = "OPENSKY";

    /** Public state vector endpoint. */
    private String openSkyBaseUrl = "https://opensky-network.org/api/states/all";

    /** Token endpoint, used only when a client id and secret are configured. */
    private String openSkyTokenUrl =
            "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";

    /** Never written in a source or a property file. */
    private String openSkyClientId;

    /** Never written in a source or a property file. */
    private String openSkyClientSecret;

    /**
     * The box the operator flies in, as {@code lamin,lamax,lomin,lomax}.
     *
     * <p>Asking for the whole planet burns the anonymous rate limit in a few
     * calls and returns thirty thousand aircraft to match against a fleet of
     * twenty-three. The default covers Europe, North Africa and the Gulf.
     */
    private double latMin = 18;
    private double latMax = 60;
    private double lonMin = -20;
    private double lonMax = 60;

    /**
     * How long a stored position is considered fresh enough not to refetch.
     *
     * <p>OpenSky serves anonymous callers a ten-second resolution and counts
     * credits per day. Thirty seconds keeps the map current without spending
     * the allowance by lunchtime.
     */
    private Duration refreshAfter = Duration.ofSeconds(30);

    /** Timeout of one state vector request. */
    private Duration timeout = Duration.ofSeconds(10);
}
