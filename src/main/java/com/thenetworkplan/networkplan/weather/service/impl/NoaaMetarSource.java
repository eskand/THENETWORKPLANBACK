package com.thenetworkplan.networkplan.weather.service.impl;

import com.thenetworkplan.networkplan.config.WeatherProperties;
import com.thenetworkplan.networkplan.weather.service.MetarSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * NOAA's public METAR files, one per station, no key required.
 *
 * <p>Each station is fetched on its own virtual thread: twelve bases cost the
 * slowest request, not the sum of twelve. A station that fails is simply
 * absent from the answer — the screen then says it has no observation, which
 * is true, instead of showing the previous one as if it were current.
 */
@Component
public class NoaaMetarSource implements MetarSource {

    private static final Logger log = LoggerFactory.getLogger(NoaaMetarSource.class);

    private final WeatherProperties properties;
    private final HttpClient client;

    public NoaaMetarSource(WeatherProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String provider() {
        return "NOAA";
    }

    @Override
    public boolean isEnabled() {
        return "NOAA".equalsIgnoreCase(properties.getProvider());
    }

    @Override
    public Map<String, String> fetch(Set<String> stationIcaoCodes) {
        Map<String, String> answers = new HashMap<>();
        if (!isEnabled() || stationIcaoCodes.isEmpty()) {
            return answers;
        }

        stationIcaoCodes.parallelStream().forEach(station -> {
            String body = fetchOne(station);
            if (body != null) {
                synchronized (answers) {
                    answers.put(station, body);
                }
            }
        });
        return answers;
    }

    private String fetchOne(String station) {
        String url = properties.getNoaaBaseUrl() + "/" + station.toUpperCase() + ".TXT";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.getTimeout())
                    .header("User-Agent", "NetPlus/1.0 (operations dashboard)")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("NOAA answered {} for {}", response.statusCode(), station);
                return null;
            }
            return response.body();
        } catch (IOException ex) {
            // A weather source being unreachable is normal and is not an error
            // of the product: the screen says "no observation" and moves on.
            log.debug("NOAA unreachable for {}: {}", station, ex.getMessage());
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
