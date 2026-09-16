package com.thenetworkplan.networkplan.reporting.service.impl;

import static com.thenetworkplan.networkplan.reporting.service.impl.OpsReportRunners.blockHours;
import static com.thenetworkplan.networkplan.reporting.service.impl.OpsReportRunners.delay;
import static com.thenetworkplan.networkplan.reporting.service.impl.OpsReportRunners.region;
import static com.thenetworkplan.networkplan.reporting.service.impl.OpsReportRunners.route;

import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportChartDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportKpiDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportSeriesDto;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The seven programme reports: totals, schedule, status, cancellations,
 * destinations, routes and passengers.
 *
 * <p><b>Cancelled sectors are in the programme and out of the averages.</b>
 * « Sum of Flights » counts what flew; « Flights Status » and « Schedule » count
 * what was planned, cancellations included, because that is the question they
 * answer. The note of each report says which of the two it did, so the two
 * totals can be reconciled rather than argued about.
 *
 * <p><b>Airborne time is estimated, and labelled as estimated.</b> Twenty
 * minutes of taxi per sector is the prototype's nominal figure; the moment OFF
 * and ON are recorded on the leg, the estimate gives way to them.
 */
@Configuration
public class FlightReportRunners {

    /** The nominal taxi allowance, in hours, behind the airborne estimate. */
    private static final double TAXI_HOURS = 0.33;

    /* ── Sum of Flights ───────────────────────────────────────────────────── */

    @Bean
    ReportRunner sumOfFlightsReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-SUM";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Totals per aircraft, type and month — flights, block time and airborne time";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Tail", "Type", "Flights", "Block time", "Airborne", "Avg sector");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<String, String> types = new HashMap<>();
                Map<String, Integer> counts = new HashMap<>();
                legs.forEach(leg -> {
                    types.putIfAbsent(leg.registration(), leg.icaoType());
                    counts.merge(leg.registration(), 1, Integer::sum);
                });

                return Rp.sumBy(legs, LegDto::registration, OpsReportRunners::blockHours).stream()
                        .map(bucket -> {
                            int count = counts.getOrDefault(bucket.key(), 0);
                            return List.of(
                                    bucket.key(),
                                    Rp.text(types.get(bucket.key())),
                                    String.valueOf(count),
                                    Rp.dur(bucket.value()),
                                    Rp.dur(Math.max(0, bucket.value() - count * TAXI_HOURS)),
                                    Rp.dur(count == 0 ? 0 : bucket.value() / count));
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                double block = legs.stream().mapToDouble(OpsReportRunners::blockHours).sum();
                double airborne = Math.max(0, block - legs.size() * TAXI_HOURS);
                long months = legs.stream()
                        .map(leg -> leg.std().toLocalDate().withDayOfMonth(1)).distinct().count();
                long types = legs.stream().map(LegDto::icaoType).distinct().count();
                long tails = legs.stream().map(LegDto::registration).distinct().count();

                return List.of(
                        Rp.kpi("Total flights", String.valueOf(legs.size()),
                                Rp.days(from, to).size() + " day period", "warn"),
                        Rp.kpi("Block time", Rp.dur(block), "gate to gate"),
                        Rp.kpi("Airborne time", Rp.dur(airborne), "block less 20 min taxi"),
                        Rp.kpi("Aircraft types", String.valueOf(types), tails + " tails"),
                        Rp.kpi("Flights / month",
                                String.valueOf(months == 0 ? 0 : Math.round((double) legs.size() / months)),
                                "average"),
                        Rp.kpi("Block / flight",
                                Rp.dur(legs.isEmpty() ? 0 : block / legs.size()), "average sector"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<LocalDate, double[]> monthly = new LinkedHashMap<>();
                legs.stream()
                        .sorted(Comparator.comparing(LegDto::std))
                        .forEach(leg -> {
                            double[] figures = monthly.computeIfAbsent(
                                    leg.std().toLocalDate().withDayOfMonth(1), key -> new double[2]);
                            figures[0]++;
                            figures[1] += blockHours(leg);
                        });

                return List.of(
                        new ReportChartDto("rpc1", "Flights and block hours per month", "line",
                                "full",
                                monthly.keySet().stream().map(Rp::monthLabel).toList(),
                                List.of(
                                        Rp.series("Flights", monthly.values().stream()
                                                .map(figures -> figures[0]).toList(), Rp.NAVY2),
                                        Rp.series("Block hours", monthly.values().stream()
                                                .map(figures -> Math.round(figures[1] * 10) / 10d)
                                                .toList(), Rp.GOLD))),
                        Rp.hbar("rpc2", "Flights by aircraft type", "half",
                                Rp.tally(legs, LegDto::icaoType), Rp.NAVY2),
                        Rp.donut("rpc3", "Block hours by aircraft type", "half",
                                Rp.sumBy(legs, LegDto::icaoType, OpsReportRunners::blockHours)));
            }

            @Override
            public String note() {
                return "Airborne time is estimated as block time less a nominal twenty minutes of "
                        + "taxi per sector: it is an estimate and is labelled as one. Record the "
                        + "actual OFF and ON times on the leg and the figure becomes exact. "
                        + "Cancelled sectors are excluded — they flew nothing and would deflate "
                        + "every average here.";
            }
        };
    }

    /* ── Schedule ─────────────────────────────────────────────────────────── */

    @Bean
    ReportRunner scheduleReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-SCHED";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Day-by-day published schedule, one line per sector with times and aircraft";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "STD", "STA", "Flight", "Tail", "Type",
                        "From", "To", "Block", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return legService.findProgrammeRange(tenantId, from, to).stream()
                        .sorted(Comparator.comparing(LegDto::std))
                        .map(leg -> List.of(
                                leg.std().toLocalDate().toString(),
                                Rp.hm(leg.std()),
                                Rp.hm(leg.sta()),
                                Rp.text(leg.flightNo()),
                                leg.registration(),
                                Rp.text(leg.icaoType()),
                                leg.depIcao(),
                                leg.arrIcao(),
                                Rp.dur(blockHours(leg)),
                                leg.status()))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                Map<LocalDate, Long> perDay = new HashMap<>();
                legs.forEach(leg -> perDay.merge(leg.std().toLocalDate(), 1L, Long::sum));
                Map.Entry<LocalDate, Long> busiest = perDay.entrySet().stream()
                        .max(Map.Entry.comparingByValue()).orElse(null);
                LegDto first = legs.stream().min(Comparator.comparing(LegDto::std)).orElse(null);
                LegDto last = legs.stream().max(Comparator.comparing(LegDto::sta)).orElse(null);

                return List.of(
                        Rp.kpi("Scheduled sectors", String.valueOf(legs.size()),
                                perDay.size() + " active days"),
                        Rp.kpi("Busiest day", busiest == null ? "0" : String.valueOf(busiest.getValue()),
                                busiest == null ? Rp.EMPTY : Rp.ddmmm(busiest.getKey()), "warn"),
                        Rp.kpi("First departure", first == null ? Rp.EMPTY : Rp.hm(first.std()),
                                "earliest STD in period"),
                        Rp.kpi("Last arrival", last == null ? Rp.EMPTY : Rp.hm(last.sta()),
                                "latest STA in period"),
                        Rp.kpi("Avg sectors / day",
                                perDay.isEmpty() ? "0" : Rp.n1((double) legs.size() / perDay.size()),
                                "active days only"),
                        Rp.kpi("Aircraft scheduled",
                                String.valueOf(legs.stream().map(LegDto::registration)
                                        .distinct().count()), "distinct tails"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                List<LocalDate> days = Rp.days(from, to);
                Map<LocalDate, Long> perDay = new HashMap<>();
                legs.forEach(leg -> perDay.merge(leg.std().toLocalDate(), 1L, Long::sum));

                double[] byHour = new double[24];
                legs.forEach(leg -> byHour[leg.std()
                        .withOffsetSameInstant(java.time.ZoneOffset.UTC).getHour()]++);
                List<String> hours = new ArrayList<>(24);
                List<Double> counts = new ArrayList<>(24);
                for (int hour = 0; hour < 24; hour++) {
                    hours.add(Rp.pad2(hour) + "h");
                    counts.add(byHour[hour]);
                }

                return List.of(
                        new ReportChartDto("rpc1", "Departures by hour (UTC)", "bar", "full",
                                hours, List.of(Rp.series("Departures", counts, Rp.NAVY2))),
                        Rp.line("rpc2", "Sectors per day", "half",
                                days.stream().map(Rp::ddmmm).toList(),
                                days.stream().map(day ->
                                        (double) perDay.getOrDefault(day, 0L)).toList(), Rp.BLUE),
                        Rp.donut("rpc3", "Sectors by type", "half",
                                Rp.tally(legs, LegDto::icaoType)));
            }

            @Override
            public String note() {
                return "Times are scheduled — STD and STA — in UTC, and every sector of the "
                        + "programme is listed, cancellations included: this is what was published, "
                        + "not what happened. Use \"Aircraft Flights\" for the same period with "
                        + "actual off and on-block times.";
            }
        };
    }

    /* ── Flights Status ───────────────────────────────────────────────────── */

    @Bean
    ReportRunner flightStatusReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-STATUS";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Where every sector stands: scheduled, airborne, completed, delayed or cancelled";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Status", "Sectors", "Share", "Block time", "Aircraft", "Example");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                return Rp.tally(legs, LegDto::status).stream()
                        .map(bucket -> {
                            List<LegDto> subset = legs.stream()
                                    .filter(leg -> bucket.key().equals(leg.status())).toList();
                            LegDto sample = subset.isEmpty() ? null : subset.get(0);
                            return List.of(
                                    bucket.key(),
                                    String.valueOf(bucket.count()),
                                    Rp.pct(bucket.value(), legs.size()) + " %",
                                    Rp.dur(subset.stream()
                                            .mapToDouble(OpsReportRunners::blockHours).sum()),
                                    String.valueOf(subset.stream().map(LegDto::registration)
                                            .distinct().count()),
                                    sample == null ? Rp.EMPTY
                                            : Rp.text(sample.flightNo()) + " " + route(sample));
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                long cancelled = count(legs, "CANCELLED");
                long scheduled = count(legs, "SCHEDULED") + count(legs, "PLANNED");
                long operated = count(legs, "AIRBORNE") + count(legs, "ENROUTE")
                        + count(legs, "DEPARTED") + count(legs, "LANDED")
                        + count(legs, "ARRIVED") + count(legs, "CLOSED")
                        + count(legs, "COMPLETED");
                long delayed = legs.stream().filter(leg -> delay(leg) > 15).count();
                int completion = Rp.pct(legs.size() - cancelled, legs.size());

                return List.of(
                        Rp.kpi("Total sectors", String.valueOf(legs.size()), from + " → " + to),
                        Rp.kpi("Scheduled", String.valueOf(scheduled), "not yet departed"),
                        Rp.kpi("Airborne / completed", String.valueOf(operated), "operated", "good"),
                        Rp.kpi("Delayed", String.valueOf(delayed),
                                "more than 15 min off blocks", Rp.countTone(delayed)),
                        Rp.kpi("Cancelled", String.valueOf(cancelled),
                                cancelled == 0 ? "none"
                                        : Rp.pct(cancelled, legs.size()) + " % of programme",
                                cancelled == 0 ? "good" : "bad"),
                        Rp.kpi("Completion rate", completion + " %", "operated vs planned",
                                Rp.rateTone(completion, 98, 95), legs.isEmpty() ? null : completion));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                List<Rp.Bucket> statuses = Rp.tally(legs, LegDto::status);
                List<String> registrations = legs.stream().map(LegDto::registration)
                        .distinct().sorted().toList();

                List<ReportSeriesDto> layers = new ArrayList<>();
                for (int index = 0; index < statuses.size(); index++) {
                    String status = statuses.get(index).key();
                    layers.add(Rp.series(status, registrations.stream()
                            .map(registration -> (double) legs.stream()
                                    .filter(leg -> registration.equals(leg.registration())
                                            && status.equals(leg.status()))
                                    .count())
                            .toList(), Rp.PALETTE.get(index % Rp.PALETTE.size())));
                }

                List<LocalDate> days = Rp.days(from, to);
                Map<LocalDate, long[]> daily = new HashMap<>();
                legs.forEach(leg -> {
                    long[] figures = daily.computeIfAbsent(leg.std().toLocalDate(),
                            key -> new long[2]);
                    figures[0]++;
                    if (delay(leg) <= 15 && !"CANCELLED".equalsIgnoreCase(leg.status())) {
                        figures[1]++;
                    }
                });

                return List.of(
                        Rp.donut("rpc1", "Status distribution", "third", statuses),
                        Rp.stacked("rpc2", "Status by aircraft", "twothirds",
                                registrations, layers),
                        new ReportChartDto("rpc3", "Daily programme vs on time", "line", "full",
                                days.stream().map(Rp::ddmmm).toList(),
                                List.of(
                                        Rp.series("Sectors", days.stream().map(day -> {
                                            long[] figures = daily.get(day);
                                            return figures == null ? 0d : (double) figures[0];
                                        }).toList(), Rp.NAVY2),
                                        Rp.series("On time", days.stream().map(day -> {
                                            long[] figures = daily.get(day);
                                            return figures == null ? 0d : (double) figures[1];
                                        }).toList(), Rp.GREEN))));
            }

            @Override
            public String note() {
                return "Status is the current state of the leg on the Timeline. A sector only "
                        + "becomes cancelled when a dispatcher cancels it, so a nil figure here "
                        + "means nothing was cancelled — not that cancellations are untracked. "
                        + "Every sector of the programme is counted, which is why the total is "
                        + "higher than in \"Sum of Flights\".";
            }

            private long count(List<LegDto> legs, String status) {
                return legs.stream().filter(leg -> status.equalsIgnoreCase(leg.status())).count();
            }
        };
    }

    /* ── Cancelled Flights ────────────────────────────────────────────────── */

    @Bean
    ReportRunner cancelledFlightsReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-CXL";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Cancelled sectors with reason, aircraft and lost block time";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "Flight", "Tail", "Route", "STD", "Lost block",
                        "Passengers", "Reason");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return cancelled(legService, tenantId, from, to).stream()
                        .map(leg -> List.of(
                                leg.std().toLocalDate().toString(),
                                Rp.text(leg.flightNo()),
                                leg.registration(),
                                route(leg),
                                Rp.hm(leg.std()),
                                Rp.dur(Rp.blockHours(leg.std(), leg.sta(), null, null)),
                                String.valueOf(leg.paxCount()),
                                reason(leg)))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                List<LegDto> cancelled = cancelled(legService, tenantId, from, to);
                double lost = cancelled.stream()
                        .mapToDouble(leg -> Rp.blockHours(leg.std(), leg.sta(), null, null)).sum();
                List<Rp.Bucket> byTail = Rp.tally(cancelled, LegDto::registration);
                List<Rp.Bucket> byRoute = Rp.tally(cancelled, OpsReportRunners::route);
                List<Rp.Bucket> byReason = Rp.tally(cancelled, FlightReportRunners::reason);
                int completion = Rp.pct(legs.size() - cancelled.size(), legs.size());

                if (cancelled.isEmpty()) {
                    return List.of(
                            Rp.kpi("Cancellations", "0", "in the selected period", "good"),
                            Rp.kpi("Sectors operated", String.valueOf(legs.size()),
                                    "none cancelled"),
                            Rp.kpi("Completion rate", "100 %", "programme integrity", "good", 100));
                }

                return List.of(
                        Rp.kpi("Cancellations", String.valueOf(cancelled.size()),
                                Rp.pct(cancelled.size(), legs.size()) + " % of programme", "bad"),
                        Rp.kpi("Lost block time", Rp.dur(lost), "not operated"),
                        Rp.kpi("Aircraft affected", String.valueOf(byTail.size()),
                                byTail.stream().limit(4).map(Rp.Bucket::key)
                                        .reduce((left, right) -> left + ", " + right).orElse("")),
                        Rp.kpi("Routes affected", String.valueOf(byRoute.size()),
                                byRoute.isEmpty() ? "" : "worst: " + byRoute.get(0).key()),
                        Rp.kpi("Main reason", byReason.isEmpty() ? Rp.EMPTY : byReason.get(0).key(),
                                byReason.isEmpty() ? "" : byReason.get(0).count() + " sectors",
                                "warn"),
                        Rp.kpi("Completion rate", completion + " %", "operated vs planned",
                                Rp.rateTone(completion, 98, 95), completion));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> cancelled = cancelled(legService, tenantId, from, to);
                if (cancelled.isEmpty()) {
                    return List.of();
                }
                return List.of(
                        Rp.hbar("rpc1", "Cancellations by reason", "half",
                                Rp.tally(cancelled, FlightReportRunners::reason), Rp.RED),
                        Rp.bar("rpc2", "Cancellations by aircraft", "half",
                                Rp.tally(cancelled, LegDto::registration), Rp.AMBER));
            }

            @Override
            public String note() {
                return "Lost block time is the scheduled block of the cancelled sectors, not a "
                        + "measured figure: nothing flew. It is what a contract-performance or "
                        + "insurance discussion needs. The reason is the cancellation remark "
                        + "recorded on the leg; a sector cancelled without one shows "
                        + "\"Unspecified\" rather than being attributed to the nearest cause.";
            }
        };
    }

    /* ── Top Destinations ─────────────────────────────────────────────────── */

    @Bean
    ReportRunner topDestinationsReport(LegService legService, AirportService airportService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-DEST";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Most-served arrival airports with block time, region and aircraft mix";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("ICAO", "Airport", "Region", "Arrivals", "Block time",
                        "Avg sector", "Aircraft", "Delay min");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<String, AirportDto> airports = airports(airportService, legs);
                return Rp.tally(legs, LegDto::arrIcao).stream()
                        .map(bucket -> {
                            List<LegDto> subset = legs.stream()
                                    .filter(leg -> bucket.key().equals(leg.arrIcao())).toList();
                            double block = subset.stream()
                                    .mapToDouble(OpsReportRunners::blockHours).sum();
                            AirportDto airport = airports.get(bucket.key());
                            return List.of(
                                    bucket.key(),
                                    airport == null ? Rp.EMPTY : Rp.text(airport.name()),
                                    region(airport),
                                    String.valueOf(bucket.count()),
                                    Rp.dur(block),
                                    Rp.dur(block / bucket.count()),
                                    String.valueOf(subset.stream().map(LegDto::registration)
                                            .distinct().count()),
                                    String.valueOf(subset.stream()
                                            .mapToLong(OpsReportRunners::delay).sum()));
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<String, AirportDto> airports = airports(airportService, legs);
                List<Rp.Bucket> destinations = Rp.tally(legs, LegDto::arrIcao);
                List<Rp.Bucket> regions = Rp.tally(legs,
                        leg -> region(airports.get(leg.arrIcao())));
                double top5 = destinations.stream().limit(5).mapToDouble(Rp.Bucket::value).sum();
                Map.Entry<String, Double> longest = averageSector(legs);
                long once = destinations.stream().filter(bucket -> bucket.count() == 1).count();

                return List.of(
                        Rp.kpi("Destinations served", String.valueOf(destinations.size()),
                                "distinct arrival airports"),
                        Rp.kpi("Top destination",
                                destinations.isEmpty() ? Rp.EMPTY : destinations.get(0).key(),
                                destinations.isEmpty() ? ""
                                        : destinations.get(0).count() + " arrivals", "warn"),
                        Rp.kpi("Top 5 concentration", Rp.pct(top5, legs.size()) + " %",
                                "of all arrivals", "neutral", Rp.pct(top5, legs.size())),
                        Rp.kpi("Regions", String.valueOf(regions.size()),
                                regions.isEmpty() ? "" : "mostly " + regions.get(0).key()),
                        Rp.kpi("Longest average sector",
                                longest == null ? Rp.EMPTY : Rp.dur(longest.getValue()),
                                longest == null ? "" : longest.getKey()),
                        Rp.kpi("Single-visit airports", String.valueOf(once),
                                "served once in period"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<String, AirportDto> airports = airports(airportService, legs);
                return List.of(
                        Rp.hbar("rpc1", "Top 15 destinations by arrivals", "full",
                                Rp.topN(Rp.tally(legs, LegDto::arrIcao), 15), Rp.NAVY2),
                        Rp.donut("rpc2", "Arrivals by region", "third",
                                Rp.tally(legs, leg -> region(airports.get(leg.arrIcao())))),
                        Rp.bar("rpc3", "Block hours by destination (top 12)", "twothirds",
                                Rp.topN(Rp.sumBy(legs, LegDto::arrIcao,
                                        OpsReportRunners::blockHours), 12), Rp.GOLD));
            }

            @Override
            public String note() {
                return "Counts arrivals only: a station the operator departs from but never lands "
                        + "at does not appear. A high single-visit count suggests ad-hoc charter "
                        + "demand rather than a stable network — worth cross-checking against the "
                        + "Commercial Pipeline before it is read as a route opportunity.";
            }
        };
    }

    /* ── Top 100 Routes ───────────────────────────────────────────────────── */

    @Bean
    ReportRunner topRoutesReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-TOP100";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Ranked route table by frequency, block time and punctuality";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("#", "Route", "Sectors", "Block time", "Avg sector",
                        "OTP D15", "Delay min", "Aircraft", "Share");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                List<Rp.Bucket> ranked = Rp.topN(Rp.tally(legs, OpsReportRunners::route), 100);
                List<List<String>> rows = new ArrayList<>(ranked.size());
                for (int index = 0; index < ranked.size(); index++) {
                    Rp.Bucket bucket = ranked.get(index);
                    List<LegDto> subset = legs.stream()
                            .filter(leg -> bucket.key().equals(route(leg))).toList();
                    double block = subset.stream().mapToDouble(OpsReportRunners::blockHours).sum();
                    long late = subset.stream().filter(leg -> delay(leg) > 15).count();
                    rows.add(List.of(
                            String.valueOf(index + 1),
                            bucket.key(),
                            String.valueOf(bucket.count()),
                            Rp.dur(block),
                            Rp.dur(block / bucket.count()),
                            Rp.pct(bucket.count() - late, bucket.count()) + " %",
                            String.valueOf(subset.stream()
                                    .mapToLong(OpsReportRunners::delay).sum()),
                            String.valueOf(subset.stream().map(LegDto::registration)
                                    .distinct().count()),
                            Rp.pct(bucket.value(), legs.size()) + " %"));
                }
                return rows;
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                List<Rp.Bucket> routes = Rp.tally(legs, OpsReportRunners::route);
                double top10 = routes.stream().limit(10).mapToDouble(Rp.Bucket::value).sum();
                long once = routes.stream().filter(bucket -> bucket.count() == 1).count();

                /* On n evalue la ponctualite qu a partir de cinq secteurs : sur
                   un seul vol, « 0 % OTP » decrit un retard, pas une ligne. */
                List<Rp.Bucket> eligible = routes.stream()
                        .filter(bucket -> bucket.count() >= 5).toList();
                Map<String, Integer> otp = new LinkedHashMap<>();
                eligible.forEach(bucket -> {
                    long late = legs.stream()
                            .filter(leg -> bucket.key().equals(route(leg)) && delay(leg) > 15)
                            .count();
                    otp.put(bucket.key(), Rp.pct(bucket.count() - late, bucket.count()));
                });
                Map.Entry<String, Integer> best = otp.entrySet().stream()
                        .max(Map.Entry.comparingByValue()).orElse(null);
                Map.Entry<String, Integer> worst = otp.entrySet().stream()
                        .min(Map.Entry.comparingByValue()).orElse(null);

                return List.of(
                        Rp.kpi("Routes flown", String.valueOf(routes.size()),
                                "distinct city pairs"),
                        Rp.kpi("Top route", routes.isEmpty() ? Rp.EMPTY : routes.get(0).key(),
                                routes.isEmpty() ? "" : routes.get(0).count() + " sectors", "warn"),
                        Rp.kpi("Top 10 share", Rp.pct(top10, legs.size()) + " %", "of all sectors",
                                "neutral", Rp.pct(top10, legs.size())),
                        Rp.kpi("Routes flown once", String.valueOf(once), "ad-hoc"),
                        Rp.kpi("Best OTP (5+ sectors)",
                                best == null ? Rp.EMPTY : best.getValue() + " %",
                                best == null ? "not enough data" : best.getKey(), "good"),
                        Rp.kpi("Worst OTP (5+ sectors)",
                                worst == null ? Rp.EMPTY : worst.getValue() + " %",
                                worst == null ? "not enough data" : worst.getKey(), "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                return List.of(
                        Rp.hbar("rpc1", "Top 20 routes by sectors", "full",
                                Rp.topN(Rp.tally(legs, OpsReportRunners::route), 20), Rp.NAVY2),
                        Rp.bar("rpc2", "Block hours — top 12 routes", "half",
                                Rp.topN(Rp.sumBy(legs, OpsReportRunners::route,
                                        OpsReportRunners::blockHours), 12), Rp.GOLD),
                        Rp.bar("rpc3", "Delay minutes — top 12 routes", "half",
                                Rp.topN(Rp.sumBy(legs, OpsReportRunners::route,
                                        OpsReportRunners::delay), 12), Rp.RED));
            }

            @Override
            public String note() {
                return "Routes are directional — TUN–NCE and NCE–TUN rank separately — which is "
                        + "how slot and handling contracts are negotiated. Punctuality per route is "
                        + "shown for every line but only ranked above five sectors: on a single "
                        + "flight, \"0 % OTP\" describes one delay, not a route.";
            }
        };
    }

    /* ── PAX by Route ─────────────────────────────────────────────────────── */

    @Bean
    ReportRunner paxByRouteReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-PAX";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Passengers carried per route, load factor and check-in completeness";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Route", "Sectors", "Passengers", "Avg / sector",
                        "Empty sectors", "Block time", "Pax / block h");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                return Rp.sumBy(legs, OpsReportRunners::route, LegDto::paxCount).stream()
                        .map(bucket -> {
                            List<LegDto> subset = legs.stream()
                                    .filter(leg -> bucket.key().equals(route(leg))).toList();
                            double block = subset.stream()
                                    .mapToDouble(OpsReportRunners::blockHours).sum();
                            return List.of(
                                    bucket.key(),
                                    String.valueOf(subset.size()),
                                    String.valueOf(bucket.count()),
                                    Rp.n1(bucket.value() / subset.size()),
                                    String.valueOf(subset.stream()
                                            .filter(leg -> leg.paxCount() == 0).count()),
                                    Rp.dur(block),
                                    block == 0 ? Rp.EMPTY : Rp.n1(bucket.value() / block));
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                long pax = legs.stream().mapToLong(LegDto::paxCount).sum();
                double block = legs.stream().mapToDouble(OpsReportRunners::blockHours).sum();
                List<Rp.Bucket> routes = Rp.sumBy(legs, OpsReportRunners::route, LegDto::paxCount);
                long empty = legs.stream().filter(leg -> leg.paxCount() == 0).count();

                return List.of(
                        Rp.kpi("Passengers carried", String.valueOf(pax),
                                legs.size() + " sectors analysed", "warn"),
                        Rp.kpi("Average per sector",
                                legs.isEmpty() ? "0" : Rp.n1((double) pax / legs.size()),
                                "pax per leg"),
                        Rp.kpi("Routes carrying pax",
                                String.valueOf(routes.stream()
                                        .filter(bucket -> bucket.value() > 0).count()),
                                "of " + Rp.tally(legs, OpsReportRunners::route).size() + " flown"),
                        Rp.kpi("Busiest route", routes.isEmpty() ? Rp.EMPTY : routes.get(0).key(),
                                routes.isEmpty() ? "" : routes.get(0).count() + " pax"),
                        Rp.kpi("Empty sectors", String.valueOf(empty),
                                empty == 0 ? "none"
                                        : Rp.pct(empty, legs.size()) + " % positioning / ferry",
                                empty == 0 ? "good" : "warn"),
                        Rp.kpi("Pax per block hour",
                                block == 0 ? Rp.EMPTY : Rp.n1(pax / block), "productivity"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                List<Rp.Bucket> routes = Rp.sumBy(legs, OpsReportRunners::route, LegDto::paxCount);
                Map<String, Long> sectors = new HashMap<>();
                legs.forEach(leg -> sectors.merge(route(leg), 1L, Long::sum));

                return List.of(
                        Rp.hbar("rpc1", "Passengers by route (top 15)", "full",
                                Rp.topN(routes, 15), Rp.NAVY2),
                        Rp.bar("rpc2", "Passengers by aircraft", "half",
                                Rp.topN(Rp.sumBy(legs, LegDto::registration,
                                        LegDto::paxCount), 12), Rp.GOLD),
                        Rp.bar("rpc3", "Average pax per sector by route (top 12)", "half",
                                Rp.topN(routes, 12).stream()
                                        .map(bucket -> new Rp.Bucket(bucket.key(),
                                                bucket.value()
                                                        / Math.max(1, sectors.getOrDefault(
                                                                bucket.key(), 1L))))
                                        .toList(), Rp.BLUE));
            }

            @Override
            public String note() {
                return "Passenger counts are the figure recorded on each leg — the same one the "
                        + "Timeline flight panel shows and the general declaration is built from. "
                        + "A sector with none is kept in the count as an empty sector rather than "
                        + "dropped: positioning and ferry legs are part of the productivity "
                        + "picture, and removing them would flatter the pax-per-block-hour figure.";
            }
        };
    }

    /* ── shared ───────────────────────────────────────────────────────────── */

    private static List<LegDto> flown(LegService legService, UUID tenantId,
                                      LocalDate from, LocalDate to) {
        return legService.findProgrammeRange(tenantId, from, to).stream()
                .filter(leg -> !"CANCELLED".equalsIgnoreCase(leg.status()))
                .toList();
    }

    private static List<LegDto> cancelled(LegService legService, UUID tenantId,
                                          LocalDate from, LocalDate to) {
        return legService.findProgrammeRange(tenantId, from, to).stream()
                .filter(leg -> "CANCELLED".equalsIgnoreCase(leg.status()))
                .sorted(Comparator.comparing(LegDto::std))
                .toList();
    }

    private static Map<String, AirportDto> airports(AirportService airportService,
                                                    List<LegDto> legs) {
        List<String> codes = legs.stream()
                .flatMap(leg -> Stream.of(leg.depIcao(), leg.arrIcao()))
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
        return codes.isEmpty() ? Map.of() : airportService.findAllByIcao(codes);
    }

    /** The destination with the longest average sector, and that average. */
    private static Map.Entry<String, Double> averageSector(List<LegDto> legs) {
        Map<String, double[]> byDestination = new HashMap<>();
        legs.forEach(leg -> {
            double[] figures = byDestination.computeIfAbsent(leg.arrIcao(), key -> new double[2]);
            figures[0] += blockHours(leg);
            figures[1]++;
        });
        return byDestination.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()[0] / entry.getValue()[1]))
                .max(Map.Entry.comparingByValue())
                .orElse(null);
    }

    /** The cancellation reason: the leg remark, or nothing pretending to be one. */
    private static String reason(LegDto leg) {
        return leg.remark() == null || leg.remark().isBlank()
                ? "Unspecified"
                : leg.remark().replaceAll("\\s*\\(.*$", "").trim();
    }
}
