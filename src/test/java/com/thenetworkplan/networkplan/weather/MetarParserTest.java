package com.thenetworkplan.networkplan.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.thenetworkplan.networkplan.weather.service.MetarParser;
import com.thenetworkplan.networkplan.weather.service.impl.MetarParserImpl;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * The decoder, tested on real messages.
 *
 * <p>These four are genuine NOAA observations of 10 September 2026, kept
 * verbatim. A decoder tested only on messages its author invented decodes
 * only the messages its author imagined.
 */
class MetarParserTest {

    private final MetarParser parser = new MetarParserImpl();
    private final OffsetDateTime received =
            OffsetDateTime.of(2026, 9, 10, 14, 20, 0, 0, ZoneOffset.UTC);

    @Test
    void decodes_a_message_with_scattered_cloud() {
        var decoded = parser.parse(
                "2026/09/10 14:00\nDTTA 101400Z 01014KT 9999 SCT026 SCT033 34/20 Q1011", received);

        assertThat(decoded).isNotNull();
        assertThat(decoded.stationIcao()).isEqualTo("DTTA");
        assertThat(decoded.observedAt()).isEqualTo(
                OffsetDateTime.of(2026, 9, 10, 14, 0, 0, 0, ZoneOffset.UTC));
        assertThat(decoded.windDirDeg()).isEqualTo(10);
        assertThat(decoded.windSpeedKt()).isEqualTo(14);
        assertThat(decoded.windGustKt()).isNull();
        assertThat(decoded.visibilityM()).isEqualTo(9999);
        assertThat(decoded.temperatureC()).isEqualTo(34);
        assertThat(decoded.dewpointC()).isEqualTo(20);
        assertThat(decoded.qnhHpa()).isEqualTo(1011);
        // SCT is not a ceiling: only BKN and OVC are.
        assertThat(decoded.ceilingFt()).isNull();
        assertThat(decoded.flightCategory()).isEqualTo("VFR");
        // The NOAA date line is dropped; the message itself is kept whole.
        assertThat(decoded.rawText()).startsWith("DTTA 101400Z");
    }

    @Test
    void stops_at_the_trend_group() {
        var decoded = parser.parse(
                "LFML 101400Z AUTO 32020KT CAVOK 26/08 Q1012 TEMPO 33020G30KT", received);

        assertThat(decoded).isNotNull();
        assertThat(decoded.windSpeedKt()).isEqualTo(20);
        // The 30 kt gust belongs to the TEMPO forecast, not to the observation.
        assertThat(decoded.windGustKt()).isNull();
        assertThat(decoded.cavok()).isTrue();
        assertThat(decoded.visibilityM()).isEqualTo(9999);
        assertThat(decoded.flightCategory()).isEqualTo("VFR");
    }

    @Test
    void reads_a_variable_wind_and_ignores_nosig() {
        var decoded = parser.parse(
                "LFPB 101400Z AUTO 26007KT 200V310 CAVOK 21/07 Q1019 NOSIG", received);

        assertThat(decoded).isNotNull();
        assertThat(decoded.windDirDeg()).isEqualTo(260);
        assertThat(decoded.windSpeedKt()).isEqualTo(7);
        assertThat(decoded.qnhHpa()).isEqualTo(1019);
    }

    @Test
    void an_overcast_low_ceiling_is_not_vfr() {
        var decoded = parser.parse(
                "EGLL 101350Z 09012KT 3000 -RA BR OVC004 12/11 Q0998", received);

        assertThat(decoded).isNotNull();
        assertThat(decoded.ceilingFt()).isEqualTo(400);
        assertThat(decoded.visibilityM()).isEqualTo(3000);
        assertThat(decoded.conditions()).contains("-RA").contains("BR");
        // 400 ft is below five hundred: the lowest band, whatever the visibility.
        assertThat(decoded.flightCategory()).isEqualTo("LIFR");
    }

    @Test
    void a_text_that_is_not_a_metar_returns_null_rather_than_a_guess() {
        assertThat(parser.parse("no data available", received)).isNull();
        assertThat(parser.parse("", received)).isNull();
        assertThat(parser.parse(null, received)).isNull();
    }

    @Test
    void negative_temperatures_are_read_as_negative() {
        var decoded = parser.parse("BIKF 101400Z 27025G40KT 9999 FEW015 M02/M05 Q0987", received);

        assertThat(decoded).isNotNull();
        assertThat(decoded.temperatureC()).isEqualTo(-2);
        assertThat(decoded.dewpointC()).isEqualTo(-5);
        assertThat(decoded.windGustKt()).isEqualTo(40);
    }
}
