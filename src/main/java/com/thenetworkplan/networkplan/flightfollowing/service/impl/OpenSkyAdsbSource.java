package com.thenetworkplan.networkplan.flightfollowing.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thenetworkplan.networkplan.config.AdsbProperties;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The OpenSky Network's public state vector service.
 *
 * <p>Anonymous by default — no key, no account. A client id and secret, when
 * present in the environment, buy a higher rate limit; they are read from
 * {@link AdsbProperties} and never appear in a source or a property file. The
 * audited prototype passed them from the browser, which its own comment
 * conceded "exposes secret + usually blocked by CORS".
 *
 * <p>The response is a positional array per aircraft, documented by OpenSky:
 * index 0 is the ICAO 24-bit address, 1 the callsign, 5 and 6 longitude and
 * latitude, 7 barometric altitude in metres, 8 on-ground, 9 velocity in metres
 * per second, 10 true track, 11 vertical rate. The indices are named in
 * constants below rather than sprinkled through the parsing, because a shifted
 * index would silently put an altitude where a speed belongs.
 */
@Component
public class OpenSkyAdsbSource implements AdsbSource {

    private static final Logger log = LoggerFactory.getLogger(OpenSkyAdsbSource.class);

    private static final int ICAO24 = 0;
    private static final int CALLSIGN = 1;
    private static final int TIME_POSITION = 3;
    private static final int LONGITUDE = 5;
    private static final int LATITUDE = 6;
    private static final int BARO_ALTITUDE = 7;
    private static final int ON_GROUND = 8;
    private static final int VELOCITY = 9;
    private static final int TRUE_TRACK = 10;
    private static final int VERTICAL_RATE = 11;

    private static final double METRES_TO_FEET = 3.280839895;
    private static final double MPS_TO_KNOTS = 1.943844;
    private static final double MPS_TO_FPM = 196.850394;

    private final AdsbProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public OpenSkyAdsbSource(AdsbProperties properties) {
        this.properties = properties;
        // Un mapper prive plutot qu injecte : ce composant ne lit qu une
        // reponse tierce, et la configuration de serialisation du produit
        // (dates, nulls, modules) ne le concerne pas. L y attacher ferait
        // dependre le decodage d OpenSky de reglages faits pour nos API.
        this.objectMapper = new ObjectMapper();
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String provider() {
        return "OPENSKY";
    }

    @Override
    public boolean isEnabled() {
        return "OPENSKY".equalsIgnoreCase(properties.getProvider());
    }

    @Override
    public List<StateVector> fetch() {
        if (!isEnabled()) {
            return List.of();
        }
        String url = properties.getOpenSkyBaseUrl()
                + "?lamin=" + properties.getLatMin()
                + "&lamax=" + properties.getLatMax()
                + "&lomin=" + properties.getLonMin()
                + "&lomax=" + properties.getLonMax();

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.getTimeout())
                    .header("Accept", "application/json")
                    .GET();

            String token = accessToken();
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response =
                    client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OpenSky answered HTTP {} — the last known positions stand",
                        response.statusCode());
                return List.of();
            }
            return parse(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception failure) {
            // A source that did not answer leaves the map as it was. It never
            // becomes a reason to draw an aircraft somewhere plausible.
            log.warn("OpenSky unreachable ({}) — the last known positions stand",
                    failure.getMessage());
            return List.of();
        }
    }

    // ----------------------------------------------------------------

    private List<StateVector> parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode states = root.path("states");
        if (!states.isArray()) {
            return List.of();
        }
        OffsetDateTime fallback = OffsetDateTime.now(ZoneOffset.UTC);

        List<StateVector> vectors = new ArrayList<>(states.size());
        for (JsonNode state : states) {
            String modeS = text(state, ICAO24);
            if (modeS == null) {
                continue;
            }
            Double latitude = decimal(state, LATITUDE);
            Double longitude = decimal(state, LONGITUDE);
            if (latitude == null || longitude == null) {
                // A state vector with no position is a transponder heard
                // without a fix. It is not a position, so it is not kept.
                continue;
            }

            Double seconds = decimal(state, TIME_POSITION);
            OffsetDateTime reportedAt = seconds == null
                    ? fallback
                    : OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds.longValue()), ZoneOffset.UTC);

            vectors.add(new StateVector(
                    modeS.trim().toLowerCase(),
                    blankToNull(text(state, CALLSIGN)),
                    latitude,
                    longitude,
                    scaled(decimal(state, BARO_ALTITUDE), METRES_TO_FEET),
                    scaled(decimal(state, VELOCITY), MPS_TO_KNOTS),
                    scaled(decimal(state, TRUE_TRACK), 1),
                    scaled(decimal(state, VERTICAL_RATE), MPS_TO_FPM),
                    state.path(ON_GROUND).isBoolean() ? state.path(ON_GROUND).asBoolean() : null,
                    reportedAt));
        }
        return vectors;
    }

    /**
     * A bearer token, or null when no credentials are configured.
     *
     * <p>Anonymous access works and is the default; this only raises the rate
     * limit. A failed token request is not fatal — the call is simply made
     * anonymously.
     */
    private String accessToken() {
        String id = properties.getOpenSkyClientId();
        String secret = properties.getOpenSkyClientSecret();
        if (id == null || id.isBlank() || secret == null || secret.isBlank()) {
            return null;
        }
        try {
            String form = "grant_type=client_credentials"
                    + "&client_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getOpenSkyTokenUrl()))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OpenSky token endpoint answered HTTP {} — falling back to anonymous",
                        response.statusCode());
                return null;
            }
            String token = objectMapper.readTree(response.body()).path("access_token").asText(null);
            return blankToNull(token);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception failure) {
            log.warn("OpenSky token request failed ({}) — falling back to anonymous",
                    failure.getMessage());
            return null;
        }
    }

    private static String text(JsonNode state, int index) {
        JsonNode node = state.path(index);
        return node.isTextual() ? node.asText() : null;
    }

    private static Double decimal(JsonNode state, int index) {
        JsonNode node = state.path(index);
        return node.isNumber() ? node.asDouble() : null;
    }

    private static Integer scaled(Double value, double factor) {
        return value == null ? null : (int) Math.round(value * factor);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
