package com.thenetworkplan.networkplan.weather.service.impl;

import com.thenetworkplan.networkplan.weather.service.MetarParser;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * A METAR decoder for the groups an OCC reads on a dashboard.
 *
 * <p>Wind, visibility, cloud, temperature, pressure and present weather. It
 * deliberately stops there: trend groups, runway visual range and remarks are
 * left in the raw text, which is always stored, rather than half-decoded into
 * fields nobody would trust.
 *
 * <p>Real messages this was written against:
 * <pre>
 * DTTA 101400Z 01014KT 9999 SCT026 SCT033 34/20 Q1011
 * LFML 101400Z AUTO 32020KT CAVOK 26/08 Q1012 TEMPO 33020G30KT
 * LFPB 101400Z AUTO 26007KT 200V310 CAVOK 21/07 Q1019 NOSIG
 * </pre>
 */
@Component
public class MetarParserImpl implements MetarParser {

    private static final Pattern STATION = Pattern.compile("^[A-Z]{4}$");
    private static final Pattern DAY_TIME = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})Z$");
    private static final Pattern WIND = Pattern.compile("^(\\d{3}|VRB)(\\d{2,3})(?:G(\\d{2,3}))?(KT|MPS)$");
    private static final Pattern VISIBILITY = Pattern.compile("^(\\d{4})(NDV)?$");
    private static final Pattern CLOUD = Pattern.compile("^(FEW|SCT|BKN|OVC|VV)(\\d{3})(CB|TCU)?$");
    private static final Pattern TEMPERATURE = Pattern.compile("^(M?\\d{2})/(M?\\d{2})$");
    private static final Pattern QNH_HPA = Pattern.compile("^Q(\\d{4})$");
    private static final Pattern QNH_INHG = Pattern.compile("^A(\\d{4})$");
    private static final Pattern PRESENT_WEATHER =
            Pattern.compile("^(-|\\+|VC)?(MI|BC|PR|DR|BL|SH|TS|FZ)?"
                    + "(DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS)+$");

    /** Groups after these keywords describe a forecast, not the observation. */
    private static final List<String> TREND_MARKERS = List.of("TEMPO", "BECMG", "NOSIG", "RMK");

    @Override
    public Decoded parse(String raw, OffsetDateTime receivedAt) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // NOAA prefixes the message with its own date line; the METAR itself
        // starts at the station identifier.
        String text = raw.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String[] tokens = text.split(" ");

        int start = 0;
        while (start < tokens.length && !STATION.matcher(tokens[start]).matches()) {
            start++;
        }
        if (start >= tokens.length) {
            return null;
        }

        String station = tokens[start];
        String reportType = start > 0 && "SPECI".equals(tokens[start - 1]) ? "SPECI" : "METAR";
        String rawText = String.join(" ", java.util.Arrays.copyOfRange(tokens, start, tokens.length));

        OffsetDateTime observedAt = null;
        Integer windDir = null;
        Integer windSpeed = null;
        Integer windGust = null;
        boolean windVariable = false;
        Integer visibility = null;
        boolean cavok = false;
        Integer ceiling = null;
        Integer temperature = null;
        Integer dewpoint = null;
        Integer qnh = null;
        List<String> weather = new ArrayList<>();

        for (int i = start + 1; i < tokens.length; i++) {
            String token = tokens[i];
            if (TREND_MARKERS.contains(token)) {
                // Everything after describes what is expected, not what is.
                break;
            }
            if ("AUTO".equals(token) || "COR".equals(token)) {
                continue;
            }

            Matcher matcher;
            if (observedAt == null && (matcher = DAY_TIME.matcher(token)).matches()) {
                observedAt = resolveObservedAt(matcher, receivedAt);
                continue;
            }
            if (windSpeed == null && (matcher = WIND.matcher(token)).matches()) {
                windVariable = "VRB".equals(matcher.group(1));
                windDir = windVariable ? null : Integer.parseInt(matcher.group(1));
                int speed = Integer.parseInt(matcher.group(2));
                int gust = matcher.group(3) == null ? -1 : Integer.parseInt(matcher.group(3));
                boolean metresPerSecond = "MPS".equals(matcher.group(4));
                windSpeed = metresPerSecond ? Math.round(speed * 1.94384f) : speed;
                windGust = gust < 0 ? null : (metresPerSecond ? Math.round(gust * 1.94384f) : gust);
                continue;
            }
            if ("CAVOK".equals(token)) {
                // Ceiling and visibility OK: ten kilometres or more, no cloud
                // below 5 000 ft. Both are then known, not missing.
                cavok = true;
                visibility = 9999;
                continue;
            }
            if (visibility == null && (matcher = VISIBILITY.matcher(token)).matches()) {
                visibility = Integer.parseInt(matcher.group(1));
                continue;
            }
            if ((matcher = CLOUD.matcher(token)).matches()) {
                String cover = matcher.group(1);
                int feet = Integer.parseInt(matcher.group(2)) * 100;
                if (("BKN".equals(cover) || "OVC".equals(cover) || "VV".equals(cover))
                        && (ceiling == null || feet < ceiling)) {
                    ceiling = feet;
                }
                continue;
            }
            if (temperature == null && (matcher = TEMPERATURE.matcher(token)).matches()) {
                temperature = celsius(matcher.group(1));
                dewpoint = celsius(matcher.group(2));
                continue;
            }
            if (qnh == null && (matcher = QNH_HPA.matcher(token)).matches()) {
                qnh = Integer.parseInt(matcher.group(1));
                continue;
            }
            if (qnh == null && (matcher = QNH_INHG.matcher(token)).matches()) {
                // Inches of mercury, hundredths: 2992 -> 1013 hPa.
                qnh = Math.round(Integer.parseInt(matcher.group(1)) / 100f * 33.8639f);
                continue;
            }
            if (PRESENT_WEATHER.matcher(token).matches()) {
                weather.add(token);
            }
        }

        if (observedAt == null) {
            // Without a time the row cannot be keyed, and an observation whose
            // moment is unknown is not an observation.
            return null;
        }

        return new Decoded(
                station,
                reportType,
                observedAt,
                rawText,
                windDir,
                windVariable,
                windSpeed,
                windGust,
                visibility,
                cavok,
                ceiling,
                temperature,
                dewpoint,
                qnh,
                weather.isEmpty() ? null : String.join(" ", weather),
                flightCategory(ceiling, visibility, cavok));
    }

    /**
     * The day-of-month group carries no month and no year.
     *
     * <p>Resolved against the moment of reception: same month normally, the
     * previous one when the day is ahead of today — which happens at a month
     * boundary, and would otherwise date the message a month in the future.
     */
    private OffsetDateTime resolveObservedAt(Matcher matcher, OffsetDateTime receivedAt) {
        int day = Integer.parseInt(matcher.group(1));
        int hour = Integer.parseInt(matcher.group(2));
        int minute = Integer.parseInt(matcher.group(3));

        OffsetDateTime reference = receivedAt.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime candidate = reference
                .withDayOfMonth(Math.min(day, reference.toLocalDate().lengthOfMonth()))
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);

        if (candidate.isAfter(reference.plusHours(6))) {
            candidate = candidate.minusMonths(1);
        }
        return candidate;
    }

    private Integer celsius(String group) {
        boolean negative = group.startsWith("M");
        int value = Integer.parseInt(negative ? group.substring(1) : group);
        return negative ? -value : value;
    }

    /**
     * The usual ceiling-and-visibility bands.
     *
     * <p>LIFR under 500 ft or 1 600 m, IFR under 1 000 ft or 5 000 m, MVFR
     * under 3 000 ft or 8 000 m, VFR above. Null when neither figure is known:
     * a category invented from nothing would be read as a clearance.
     */
    private String flightCategory(Integer ceilingFt, Integer visibilityM, boolean cavok) {
        if (cavok) {
            return "VFR";
        }
        if (ceilingFt == null && visibilityM == null) {
            return null;
        }
        int ceiling = ceilingFt == null ? Integer.MAX_VALUE : ceilingFt;
        int visibility = visibilityM == null ? Integer.MAX_VALUE : visibilityM;

        if (ceiling < 500 || visibility < 1600) {
            return "LIFR";
        }
        if (ceiling < 1000 || visibility < 5000) {
            return "IFR";
        }
        if (ceiling < 3000 || visibility < 8000) {
            return "MVFR";
        }
        return "VFR";
    }
}
