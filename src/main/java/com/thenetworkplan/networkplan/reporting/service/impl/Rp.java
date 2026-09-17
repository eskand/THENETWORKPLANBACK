package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportChartDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportKpiDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportSeriesDto;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * The shared vocabulary of every report runner.
 *
 * <p><b>Why one toolkit rather than helpers per file.</b> The approved
 * prototype computes its twenty-eight reports out of nine functions — {@code
 * tally}, {@code sumBy}, {@code topN}, {@code dur}, {@code pct} — and that is
 * why its figures agree with each other. Two runners that each wrote their own
 * « block hours » formatter would eventually print 6:15 and 6.3 for the same
 * sector, and an operator would have to decide which one to quote.
 *
 * <p><b>Block time is decimal hours inside, h:mm outside.</b> Summing h:mm
 * strings is how rounding errors accumulate; formatting happens once, at the
 * edge, in {@link #dur(double)}.
 *
 * <p><b>The palette is the prototype's, to the hex digit.</b> Cancelled is red
 * on every chart of every report because the colour is looked up by meaning
 * here, not chosen by position in a list.
 */
final class Rp {

    private Rp() {
    }

    /* ── the palette, copied from the prototype's `var C` ─────────────────── */

    static final String NAVY = "#0d1b3e";
    static final String NAVY2 = "#1B2D6B";
    static final String GOLD = "#C9A227";
    static final String RED = "#C8202F";
    static final String GREEN = "#1f9d5c";
    static final String BLUE = "#2f6fb0";
    static final String AMBER = "#b8790a";
    static final String PURPLE = "#7b4fc9";
    static final String GREY = "#c7cbd6";

    static final List<String> PALETTE = List.of(NAVY2, GOLD, GREEN, BLUE, RED,
            PURPLE, AMBER, "#0f766e", "#9333ea", "#64748b");

    /** The em dash the whole product uses for « no value recorded ». */
    static final String EMPTY = "—";

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /* ── counting ─────────────────────────────────────────────────────────── */

    /** One bucket per distinct key, counted, heaviest first. Null keys drop. */
    static <T> List<Bucket> tally(Collection<T> rows, Function<T, String> key) {
        return sumBy(rows, key, row -> 1d);
    }

    /**
     * One bucket per distinct key, summed, heaviest first.
     *
     * <p>Rows whose key is null or blank are dropped rather than gathered under
     * « — »: a bar chart with an « unknown » column taller than the rest says
     * something about the data entry, not about the operation, and the table
     * below already shows those rows.
     */
    static <T> List<Bucket> sumBy(Collection<T> rows, Function<T, String> key,
                                  ToDoubleFunction<T> value) {
        Map<String, Double> sums = new LinkedHashMap<>();
        for (T row : rows) {
            String k = key.apply(row);
            if (k == null || k.isBlank()) {
                continue;
            }
            sums.merge(k, value.applyAsDouble(row), Double::sum);
        }
        return sums.entrySet().stream()
                .map(entry -> new Bucket(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(Bucket::value).reversed())
                .toList();
    }

    /** The heaviest n buckets. A chart with forty bars is a chart with none. */
    static List<Bucket> topN(List<Bucket> buckets, int n) {
        return buckets.size() <= n ? buckets : buckets.subList(0, n);
    }

    /** The value of one bucket, zero when the key never appeared. */
    static double at(List<Bucket> buckets, String key) {
        for (Bucket bucket : buckets) {
            if (bucket.key().equals(key)) {
                return bucket.value();
            }
        }
        return 0d;
    }

    /** One bucket of a tally or a sum. */
    record Bucket(String key, double value) {

        int count() {
            return (int) Math.round(value);
        }
    }

    /* ── formatting ───────────────────────────────────────────────────────── */

    /** Decimal hours as a duration: 26.5 stays 26:30, it does not wrap. */
    static String dur(double hours) {
        long h = (long) Math.floor(hours);
        long m = Math.round((hours - h) * 60);
        if (m == 60) {
            h++;
            m = 0;
        }
        return h + ":" + pad2(m);
    }

    /** Block minutes as h:mm. The stored unit everywhere in crew and ops. */
    static String hhmm(long minutes) {
        return minutes / 60 + ":" + pad2(Math.abs(minutes % 60));
    }

    /** A time of day, UTC, as the operator writes it on a flight plan. */
    static String hm(OffsetDateTime at) {
        if (at == null) {
            return EMPTY;
        }
        OffsetDateTime utc = at.withOffsetSameInstant(ZoneOffset.UTC);
        return pad2(utc.getHour()) + ":" + pad2(utc.getMinute());
    }

    /** « 14 Sep » — the prototype's date, short enough for an axis label. */
    static String ddmmm(LocalDate date) {
        return date == null ? EMPTY : date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1];
    }

    /** « Sep 2026 » — the month roll-up label. */
    static String monthLabel(LocalDate date) {
        return MONTHS[date.getMonthValue() - 1] + " " + date.getYear();
    }

    /**
     * One decimal place, with a decimal point whatever the server locale.
     *
     * <p>{@code String.format} without a locale uses the JVM default, and a
     * server started in a French locale would print « 1,9 » next to « 93:20 »
     * on the same screen. Worse, that figure goes into the CSV export, where a
     * comma is the column separator.
     */
    static String n1(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    /** A whole percent. Zero when there is no denominator: never « NaN% ». */
    static int pct(double part, double total) {
        return total == 0 ? 0 : (int) Math.round(part / total * 100);
    }

    static String pad2(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? EMPTY : String.valueOf(value);
    }

    static String iso(LocalDate date) {
        return date == null ? EMPTY : date.toString();
    }

    /** Block hours of a leg, from the actuals when they exist, else the plan. */
    static double blockHours(OffsetDateTime std, OffsetDateTime sta,
                             OffsetDateTime outAt, OffsetDateTime inAt) {
        OffsetDateTime start = outAt != null ? outAt : std;
        OffsetDateTime end = inAt != null ? inAt : sta;
        if (start == null || end == null || end.isBefore(start)) {
            return 0d;
        }
        return Duration.between(start, end).toMinutes() / 60d;
    }

    /** Minutes late off blocks. Early departures are not negative delay. */
    static long delayMinutes(OffsetDateTime std, OffsetDateTime outAt) {
        if (std == null || outAt == null) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.MINUTES.between(std, outAt));
    }

    /** The punctuality bucket of the prototype, D0 / D15 / D60 / over. */
    static String delayBucket(long minutes) {
        if (minutes <= 0) {
            return "On time (D0)";
        }
        if (minutes <= 15) {
            return "Within 15 min";
        }
        if (minutes <= 60) {
            return "16–60 min";
        }
        return "Over 60 min";
    }

    static final List<String> DELAY_BUCKETS =
            List.of("On time (D0)", "Within 15 min", "16–60 min", "Over 60 min");

    static String delayBucketColour(String bucket) {
        return switch (bucket) {
            case "On time (D0)" -> GREEN;
            case "Within 15 min" -> BLUE;
            case "16–60 min" -> AMBER;
            default -> RED;
        };
    }

    /** Every day of the window, inclusive. The axis of every daily chart. */
    static List<LocalDate> days(LocalDate from, LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            out.add(day);
        }
        return out;
    }

    /* ── figures ──────────────────────────────────────────────────────────── */

    static ReportKpiDto kpi(String label, String value, String sub) {
        return new ReportKpiDto(label, value, sub, "neutral", null);
    }

    static ReportKpiDto kpi(String label, String value, String sub, String tone) {
        return new ReportKpiDto(label, value, sub, tone, null);
    }

    static ReportKpiDto kpi(String label, String value, String sub, String tone, Integer bar) {
        return new ReportKpiDto(label, value, sub, tone, bar);
    }

    /**
     * The tone of a rate: green above the good threshold, amber above the
     * acceptable one, red below both.
     */
    static String rateTone(int percent, int good, int acceptable) {
        if (percent >= good) {
            return "good";
        }
        return percent >= acceptable ? "warn" : "bad";
    }

    /** Good when nothing is outstanding, amber when something is. */
    static String countTone(long outstanding) {
        return outstanding == 0 ? "good" : "warn";
    }

    /* ── charts ───────────────────────────────────────────────────────────── */

    static ReportChartDto bar(String id, String title, String width,
                              List<Bucket> buckets, String colour) {
        return new ReportChartDto(id, title, "bar", width,
                buckets.stream().map(Bucket::key).toList(),
                List.of(new ReportSeriesDto(title, values(buckets), colour)));
    }

    /**
     * A bar chart laid on its side.
     *
     * <p>Used wherever the categories are names rather than dates — routes,
     * aerodromes, delay causes. « Rotation cascade from TNP123 » cannot be read
     * under a vertical bar, and rotating the label to fit is how a chart becomes
     * a picture of text.
     */
    static ReportChartDto hbar(String id, String title, String width,
                               List<Bucket> buckets, String colour) {
        return new ReportChartDto(id, title, "hbar", width,
                buckets.stream().map(Bucket::key).toList(),
                List.of(new ReportSeriesDto(title, values(buckets), colour)));
    }

    static ReportChartDto line(String id, String title, String width,
                               List<String> labels, List<Double> data, String colour) {
        return new ReportChartDto(id, title, "line", width, labels,
                List.of(new ReportSeriesDto(title, data, colour)));
    }

    static ReportChartDto donut(String id, String title, String width, List<Bucket> buckets) {
        return donut(id, title, width, buckets, null);
    }

    /**
     * A donut whose slices carry their own colours.
     *
     * <p>Pass {@code colours} whenever the slices mean something — a status, a
     * severity — so the same word keeps the same colour when the period changes
     * and a slice disappears.
     */
    static ReportChartDto donut(String id, String title, String width,
                                List<Bucket> buckets, List<String> colours) {
        return new ReportChartDto(id, title, "donut", width,
                buckets.stream().map(Bucket::key).toList(),
                List.of(new ReportSeriesDto(title, values(buckets), null, colours)));
    }

    /**
     * Several series over the same categories, stacked.
     *
     * <p>The series arrive already built: a stacked chart is the one place where
     * the caller knows what the layers are and this class cannot guess.
     */
    static ReportChartDto stacked(String id, String title, String width,
                                  List<String> labels, List<ReportSeriesDto> series) {
        return new ReportChartDto(id, title, "stacked", width, labels, series);
    }

    static ReportSeriesDto series(String label, List<Double> data, String colour) {
        return new ReportSeriesDto(label, data, colour);
    }

    private static List<Double> values(List<Bucket> buckets) {
        return buckets.stream().map(bucket -> Math.round(bucket.value() * 10) / 10d).toList();
    }
}
