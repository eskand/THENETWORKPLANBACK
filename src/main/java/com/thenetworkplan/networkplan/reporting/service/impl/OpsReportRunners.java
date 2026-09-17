package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.ops.dto.LegDelayDto;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportChartDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportKpiDto;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The four network reports of the approved catalogue, plus the delay register.
 *
 * <p><b>All of them read the same sector list.</b> Utilisation, punctuality,
 * route statistics and the ops journal are four readings of one programme, and
 * the prototype computes them that way on purpose: a utilisation report that
 * counted a cancelled sector and a route report that did not would each be
 * defensible on their own and irreconcilable together.
 *
 * <p><b>Block time is the actuals when they exist.</b> A sector with a recorded
 * OUT and IN is measured; one without is measured on the schedule. Mixing the
 * two in one column is honest — it is what the operator has — and the note of
 * every report says so.
 */
@Configuration
public class OpsReportRunners {

    private static final int ON_TIME_TOLERANCE_MINUTES = 15;

    /* ── OPS 01 — block time per tail, and by month ───────────────────────── */

    @Bean
    ReportRunner fleetUtilisationReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-UTIL";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Block time, sectors and productivity per tail — with monthly breakdown";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Tail", "Type", "Sectors", "Block h", "Avg sector",
                        "Block/day", "% of total");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                double total = legs.stream().mapToDouble(OpsReportRunners::blockHours).sum();
                long dayCount = Math.max(1, Rp.days(from, to).size());
                Map<String, String> types = new HashMap<>();
                legs.forEach(leg -> types.putIfAbsent(leg.registration(), leg.icaoType()));
                Map<String, Integer> sectors = new HashMap<>();
                legs.forEach(leg -> sectors.merge(leg.registration(), 1, Integer::sum));

                return Rp.sumBy(legs, LegDto::registration, OpsReportRunners::blockHours).stream()
                        .map(bucket -> {
                            int count = sectors.getOrDefault(bucket.key(), 0);
                            return List.of(
                                    bucket.key(),
                                    Rp.text(types.get(bucket.key())),
                                    String.valueOf(count),
                                    Rp.dur(bucket.value()),
                                    Rp.dur(count == 0 ? 0 : bucket.value() / count),
                                    Rp.dur(bucket.value() / dayCount),
                                    Rp.pct(bucket.value(), total) + " %");
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                double block = legs.stream().mapToDouble(OpsReportRunners::blockHours).sum();
                long dayCount = Math.max(1, Rp.days(from, to).size());
                long tails = legs.stream().map(LegDto::registration).distinct().count();
                LegDto longest = legs.stream()
                        .max(Comparator.comparingDouble(OpsReportRunners::blockHours))
                        .orElse(null);

                return List.of(
                        Rp.kpi("Block hours", Rp.dur(block), dayCount + " day period", "warn"),
                        Rp.kpi("Sectors flown", String.valueOf(legs.size()),
                                Rp.n1((double) legs.size() / dayCount) + " / day"),
                        Rp.kpi("Avg block / sector",
                                Rp.dur(legs.isEmpty() ? 0 : block / legs.size()), "gate to gate"),
                        Rp.kpi("Tails used", String.valueOf(tails), "distinct registrations"),
                        Rp.kpi("Utilisation",
                                Rp.dur(tails == 0 ? 0 : block / tails / dayCount),
                                "per tail per day"),
                        Rp.kpi("Longest sector",
                                longest == null ? Rp.EMPTY : Rp.dur(blockHours(longest)),
                                longest == null ? "no data"
                                        : route(longest) + " · " + longest.registration()));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                List<LocalDate> days = Rp.days(from, to);
                Map<LocalDate, Double> daily = dailyBlock(legs);
                Map<String, Double> monthly = new LinkedHashMap<>();
                legs.forEach(leg -> monthly.merge(
                        Rp.monthLabel(leg.std().toLocalDate().withDayOfMonth(1)),
                        blockHours(leg), Double::sum));

                return List.of(
                        Rp.bar("rpc1", "Block hours by aircraft", "half",
                                Rp.topN(Rp.sumBy(legs, LegDto::registration,
                                        OpsReportRunners::blockHours), 12), Rp.NAVY2),
                        Rp.line("rpc2", "Daily block hours", "half",
                                days.stream().map(Rp::ddmmm).toList(),
                                days.stream().map(day -> daily.getOrDefault(day, 0d)).toList(),
                                Rp.GOLD),
                        Rp.donut("rpc3", "Block hours by type", "third",
                                Rp.sumBy(legs, LegDto::icaoType, OpsReportRunners::blockHours)),
                        new ReportChartDto("rpc4", "Block hours by month", "bar", "twothirds",
                                List.copyOf(monthly.keySet()),
                                List.of(Rp.series("Block h",
                                        monthly.values().stream()
                                                .map(value -> Math.round(value * 10) / 10d).toList(),
                                        Rp.BLUE))));
            }

            @Override
            public String note() {
                return "Block time is scheduled off-blocks to on-blocks, adjusted by any recorded "
                        + "OUT and IN. Cancelled sectors generate no block time and are excluded "
                        + "here — they are counted in Cancelled Flights. Tails parked for "
                        + "maintenance produce no programme and so do not appear at all: an absent "
                        + "row is not a zero-utilisation tail, it is a tail that was never "
                        + "scheduled.";
            }
        };
    }

    /* ── OPS 02 — punctuality and the causes behind it ────────────────────── */

    @Bean
    ReportRunner onTimePerformanceReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-OTP";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "OTP, delay minutes and root-cause Pareto from recorded OCC delays";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "Flight", "Tail", "Route", "STD", "ATD",
                        "Delay", "Recorded cause");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                Map<UUID, String> causes = causeByLeg(legService, tenantId, from, to);
                Map<UUID, Long> recorded = recordedByLeg(legService, tenantId, from, to);
                return flown(legService, tenantId, from, to).stream()
                        .filter(leg -> delay(leg, recorded) > 0)
                        .sorted(Comparator.comparingLong((LegDto leg) ->
                                delay(leg, recorded)).reversed())
                        .map(leg -> List.of(
                                Rp.ddmmm(leg.std().toLocalDate()),
                                Rp.text(leg.flightNo()),
                                leg.registration(),
                                route(leg),
                                Rp.hm(leg.std()),
                                Rp.hm(leg.outAt()),
                                delay(leg, recorded) + " min",
                                causes.getOrDefault(leg.id(), "Unspecified")))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<UUID, Long> recorded = recordedByLeg(legService, tenantId, from, to);
                List<LegDto> delayed = legs.stream()
                        .filter(leg -> delay(leg, recorded) > 0).toList();
                long within15 = legs.stream()
                        .filter(leg -> delay(leg, recorded) <= ON_TIME_TOLERANCE_MINUTES).count();
                long totalDelay = delayed.stream()
                        .mapToLong(leg -> delay(leg, recorded)).sum();
                LegDto worst = delayed.stream()
                        .max(Comparator.comparingLong((LegDto leg) -> delay(leg, recorded)))
                        .orElse(null);
                int otp = Rp.pct(within15, legs.size());

                return List.of(
                        Rp.kpi("OTP (D15)", otp + " %",
                                within15 + " of " + legs.size() + " sectors",
                                Rp.rateTone(otp, 90, 75), legs.isEmpty() ? null : otp),
                        Rp.kpi("Delayed sectors", String.valueOf(delayed.size()),
                                Rp.pct(delayed.size(), legs.size()) + " % of movements",
                                Rp.countTone(delayed.size())),
                        Rp.kpi("Total delay", Rp.dur(totalDelay / 60d), totalDelay + " minutes"),
                        Rp.kpi("Avg per delay",
                                delayed.isEmpty() ? Rp.EMPTY
                                        : Math.round((double) totalDelay / delayed.size()) + " min",
                                "delayed sectors only"),
                        Rp.kpi("Worst delay",
                                worst == null ? Rp.EMPTY : delay(worst, recorded) + " min",
                                worst == null ? "none recorded"
                                        : Rp.text(worst.flightNo()) + " · " + route(worst),
                                worst != null && delay(worst, recorded) > 60
                                        ? "bad" : "neutral"),
                        Rp.kpi("Delay-free days",
                                delayFreeDays(legs, recorded) + " / " + flyingDays(legs),
                                "no delay recorded", "good"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<UUID, Long> recorded = recordedByLeg(legService, tenantId, from, to);
                List<LegDto> delayed = legs.stream()
                        .filter(leg -> delay(leg, recorded) > 0).toList();
                List<LocalDate> days = Rp.days(from, to);
                Map<UUID, String> causes = causeByLeg(legService, tenantId, from, to);

                /* Les tranches gardent l ordre de la reglementation, pas celui
                   du volume : D0 avant D15 avant le reste, sinon la lecture
                   change de sens d une periode a l autre. */
                List<Rp.Bucket> buckets = new ArrayList<>();
                Map<String, Long> counted = new LinkedHashMap<>();
                legs.forEach(leg ->
                        counted.merge(Rp.delayBucket(delay(leg, recorded)), 1L, Long::sum));
                Rp.DELAY_BUCKETS.stream()
                        .filter(counted::containsKey)
                        .forEach(name -> buckets.add(new Rp.Bucket(name, counted.get(name))));

                List<Rp.Bucket> pareto = Rp.topN(Rp.sumBy(delayed,
                        leg -> shorten(causes.getOrDefault(leg.id(), "Unspecified")),
                        leg -> delay(leg, recorded)), 8);

                Map<LocalDate, long[]> punctual = new LinkedHashMap<>();
                legs.forEach(leg -> {
                    long[] figures = punctual.computeIfAbsent(
                            leg.std().toLocalDate(), key -> new long[2]);
                    figures[0]++;
                    if (delay(leg, recorded) <= ON_TIME_TOLERANCE_MINUTES) {
                        figures[1]++;
                    }
                });

                return List.of(
                        Rp.donut("rpc1", "Punctuality distribution", "third", buckets,
                                buckets.stream().map(bucket ->
                                        Rp.delayBucketColour(bucket.key())).toList()),
                        Rp.hbar("rpc2", "Delay causes — Pareto (minutes)", "twothirds",
                                pareto, Rp.RED),
                        Rp.line("rpc3", "Daily OTP %", "half",
                                days.stream().map(Rp::ddmmm).toList(),
                                days.stream().map(day -> {
                                    long[] figures = punctual.get(day);
                                    return figures == null ? 0d
                                            : (double) Rp.pct(figures[1], figures[0]);
                                }).toList(), Rp.GREEN),
                        Rp.bar("rpc4", "Delay minutes by aircraft", "half",
                                Rp.topN(Rp.sumBy(delayed, LegDto::registration,
                                        leg -> delay(leg, recorded)), 12), Rp.AMBER));
            }

            @Override
            public String note() {
                return "OTP uses the industry D15 convention: a sector counts as on time if it "
                        + "left within " + ON_TIME_TOLERANCE_MINUTES + " minutes of schedule. Only "
                        + "sectors carrying an actual off-block time are measured — one that never "
                        + "departed is excluded rather than counted as punctual, which is how a "
                        + "report reaches 100 %. The cause is the remark on the OCC delay record; "
                        + "a delayed sector with no record shows \"Unspecified\" rather than being "
                        + "attributed to the nearest code.";
            }
        };
    }

    /* ── OPS 03 — the network, route by route ─────────────────────────────── */

    @Bean
    ReportRunner routeStatisticsReport(LegService legService, AirportService airportService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-ROUTE";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Airports served, route frequency, stage length and regional split";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Route", "Sectors", "Block h", "Avg block", "Delay min",
                        "Destination", "Region");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<String, AirportDto> airports = airports(airportService, legs);
                Map<String, Double> block = new HashMap<>();
                Map<String, Long> delays = new HashMap<>();
                Map<String, String> destination = new HashMap<>();
                legs.forEach(leg -> {
                    block.merge(route(leg), blockHours(leg), Double::sum);
                    delays.merge(route(leg), delay(leg), Long::sum);
                    destination.putIfAbsent(route(leg), leg.arrIcao());
                });

                return Rp.tally(legs, OpsReportRunners::route).stream()
                        .map(bucket -> {
                            String arrival = destination.get(bucket.key());
                            AirportDto airport = airports.get(arrival);
                            double hours = block.getOrDefault(bucket.key(), 0d);
                            return List.of(
                                    bucket.key(),
                                    String.valueOf(bucket.count()),
                                    Rp.dur(hours),
                                    Rp.dur(hours / bucket.count()),
                                    String.valueOf(delays.getOrDefault(bucket.key(), 0L)),
                                    airport == null ? Rp.text(arrival)
                                            : Rp.text(airport.name()),
                                    region(airport));
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<String, AirportDto> airports = airports(airportService, legs);
                List<Rp.Bucket> movements = movements(legs);
                List<Rp.Bucket> routes = Rp.tally(legs, OpsReportRunners::route);
                double block = legs.stream().mapToDouble(OpsReportRunners::blockHours).sum();
                List<Rp.Bucket> regions = Rp.tally(legs,
                        leg -> region(airports.get(leg.arrIcao())));

                return List.of(
                        Rp.kpi("Airports served", String.valueOf(movements.size()),
                                "departures + arrivals"),
                        Rp.kpi("Distinct routes", String.valueOf(routes.size()),
                                legs.size() + " sectors"),
                        Rp.kpi("Busiest airport",
                                movements.isEmpty() ? Rp.EMPTY : movements.get(0).key(),
                                movements.isEmpty() ? "no data"
                                        : movements.get(0).count() + " movements", "warn"),
                        Rp.kpi("Busiest route", routes.isEmpty() ? Rp.EMPTY : routes.get(0).key(),
                                routes.isEmpty() ? "no data" : routes.get(0).count() + " sectors"),
                        Rp.kpi("Avg stage length",
                                Rp.dur(legs.isEmpty() ? 0 : block / legs.size()),
                                "block time per sector"),
                        Rp.kpi("Regions touched", String.valueOf(regions.size()),
                                "per airport registry"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = flown(legService, tenantId, from, to);
                Map<String, AirportDto> airports = airports(airportService, legs);

                return List.of(
                        Rp.hbar("rpc1", "Top routes by sectors", "half",
                                Rp.topN(Rp.tally(legs, OpsReportRunners::route), 10), Rp.NAVY2),
                        Rp.hbar("rpc2", "Top airports by movements", "half",
                                Rp.topN(movements(legs), 10), Rp.BLUE),
                        Rp.donut("rpc3", "Sectors by destination region", "third",
                                Rp.tally(legs, leg -> region(airports.get(leg.arrIcao())))),
                        Rp.bar("rpc4", "Block hours by route (top 12)", "twothirds",
                                Rp.topN(Rp.sumBy(legs, OpsReportRunners::route,
                                        OpsReportRunners::blockHours), 12), Rp.GOLD));
            }

            @Override
            public String note() {
                return "Movements count each sector twice, once at departure and once at arrival: "
                        + "that is the convention used for airport handling and slot volumes, and "
                        + "it is why the movement total is double the sector count. A route is "
                        + "directed — LFPB–DTTA and DTTA–LFPB are two lines, because they are two "
                        + "sectors with different block times. Regions come from the aerodrome "
                        + "directory; a station with no region on file shows \"—\" rather than "
                        + "being assigned to the nearest one.";
            }
        };
    }

    /* ── OPS 04 — the journal every other report is built on ──────────────── */

    @Bean
    ReportRunner aircraftFlightsReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-LOG";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Complete operated leg list for the period — the exportable ops journal";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "Flight", "Tail", "Type", "From", "To", "STD", "STA",
                        "ATD", "ATA", "Block", "Delay", "Status", "Operation");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return legService.findProgrammeRange(tenantId, from, to).stream()
                        .sorted(Comparator.comparing(LegDto::std))
                        .map(leg -> List.of(
                                leg.std().toLocalDate().toString(),
                                Rp.text(leg.flightNo()),
                                leg.registration(),
                                Rp.text(leg.icaoType()),
                                leg.depIcao(),
                                leg.arrIcao(),
                                Rp.hm(leg.std()),
                                Rp.hm(leg.sta()),
                                Rp.hm(leg.outAt()),
                                Rp.hm(leg.inAt()),
                                Rp.dur(blockHours(leg)),
                                delay(leg) == 0 ? Rp.EMPTY : delay(leg) + "'",
                                leg.status(),
                                Rp.text(leg.flightType())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                double block = legs.stream().mapToDouble(OpsReportRunners::blockHours).sum();
                Map<LocalDate, Long> perDay = new HashMap<>();
                legs.forEach(leg -> perDay.merge(leg.std().toLocalDate(), 1L, Long::sum));

                return List.of(
                        Rp.kpi("Sectors logged", String.valueOf(legs.size()), from + " → " + to),
                        Rp.kpi("Block hours", Rp.dur(block), "total for period", "warn"),
                        Rp.kpi("Aircraft used",
                                String.valueOf(legs.stream().map(LegDto::registration)
                                        .distinct().count()), "distinct tails"),
                        Rp.kpi("Flight numbers",
                                String.valueOf(legs.stream().map(LegDto::flightNo)
                                        .filter(value -> value != null && !value.isBlank())
                                        .distinct().count()), "distinct callsigns"),
                        Rp.kpi("Delayed",
                                String.valueOf(legs.stream().filter(leg -> delay(leg) > 0).count()),
                                "with recorded delay", "warn"),
                        Rp.kpi("Busiest day",
                                String.valueOf(perDay.values().stream()
                                        .mapToLong(Long::longValue).max().orElse(0)),
                                "sectors in one day"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<LegDto> legs = legService.findProgrammeRange(tenantId, from, to);
                long[] weekday = new long[7];
                legs.forEach(leg -> weekday[leg.std().toLocalDate().getDayOfWeek().getValue() - 1]++);
                List<Rp.Bucket> byDay = new ArrayList<>();
                for (DayOfWeek day : DayOfWeek.values()) {
                    byDay.add(new Rp.Bucket(
                            day.name().charAt(0) + day.name().substring(1, 3).toLowerCase(),
                            weekday[day.getValue() - 1]));
                }

                List<Rp.Bucket> statuses = Rp.tally(legs, LegDto::status);
                return List.of(
                        Rp.donut("rpc1", "Sectors by status", "third", statuses,
                                statuses.stream().map(bucket ->
                                        statusColour(bucket.key())).toList()),
                        new ReportChartDto("rpc2", "Sectors by day of week", "bar", "third",
                                byDay.stream().map(Rp.Bucket::key).toList(),
                                List.of(Rp.series("Sectors",
                                        byDay.stream().map(Rp.Bucket::value).toList(), Rp.NAVY2))),
                        Rp.donut("rpc3", "Operation type", "third",
                                Rp.tally(legs, LegDto::flightType)));
            }

            @Override
            public String note() {
                return "All times UTC. Every sector of the period is here, cancellations included, "
                        + "which is why the sector count is higher than in the utilisation report. "
                        + "This is the source table behind every other operations report: export "
                        + "it for post-flight billing, crew pay or an authority filing, and the "
                        + "figures upstream can be reconstructed from it line by line.";
            }
        };
    }

    /* ── the delay register — the product's own, not the prototype's ──────── */

    @Bean
    ReportRunner delayReport(LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "OPS-DELAY";
            }

            @Override
            public String module() {
                return "Flights";
            }

            @Override
            public String subtitle() {
                return "Delay minutes and occurrences per IATA code";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("IATA code", "Occurrences", "Total minutes", "Average minutes");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return legService.countDelaysByCode(tenantId, from, to).entrySet().stream()
                        .sorted(Comparator.comparingLong((Map.Entry<String, long[]> entry) ->
                                entry.getValue()[0]).reversed())
                        .map(entry -> List.of(
                                entry.getKey(),
                                String.valueOf(entry.getValue()[1]),
                                String.valueOf(entry.getValue()[0]),
                                String.valueOf(entry.getValue()[1] == 0
                                        ? 0
                                        : entry.getValue()[0] / entry.getValue()[1])))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                Map<String, long[]> byCode = legService.countDelaysByCode(tenantId, from, to);
                long minutes = byCode.values().stream().mapToLong(figures -> figures[0]).sum();
                long occurrences = byCode.values().stream().mapToLong(figures -> figures[1]).sum();
                Map.Entry<String, long[]> worst = byCode.entrySet().stream()
                        .max(Comparator.comparingLong(entry -> entry.getValue()[0])).orElse(null);

                return List.of(
                        Rp.kpi("Delay records", String.valueOf(occurrences),
                                byCode.size() + " distinct codes"),
                        Rp.kpi("Total delay", Rp.dur(minutes / 60d), minutes + " minutes", "warn"),
                        Rp.kpi("Average per record",
                                occurrences == 0 ? Rp.EMPTY
                                        : Math.round((double) minutes / occurrences) + " min",
                                "across every coded delay"),
                        Rp.kpi("Heaviest code", worst == null ? Rp.EMPTY : worst.getKey(),
                                worst == null ? "none recorded"
                                        : worst.getValue()[0] + " minutes over "
                                                + worst.getValue()[1] + " records",
                                worst == null ? "good" : "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<Rp.Bucket> byCode = legService.countDelaysByCode(tenantId, from, to)
                        .entrySet().stream()
                        .map(entry -> new Rp.Bucket(entry.getKey(), entry.getValue()[0]))
                        .sorted(Comparator.comparingDouble(Rp.Bucket::value).reversed())
                        .toList();
                return List.of(Rp.hbar("rpc1", "Delay minutes by code", "full",
                        Rp.topN(byCode, 12), Rp.AMBER));
            }

            @Override
            public String note() {
                return "One row per delay code recorded against a leg departing inside the window. "
                        + "A delay with no code is not in this table: it is in the punctuality "
                        + "report, under \"Unspecified\".";
            }
        };
    }

    /* ── shared reading of the programme ──────────────────────────────────── */

    /**
     * The sectors that actually flew.
     *
     * <p>A cancelled sector has no block time and no departure: counting it
     * would drag every average down and make punctuality depend on how many
     * flights were called off, which is a different report.
     */
    private static List<LegDto> flown(LegService legService, UUID tenantId,
                                      LocalDate from, LocalDate to) {
        return legService.findProgrammeRange(tenantId, from, to).stream()
                .filter(leg -> !"CANCELLED".equalsIgnoreCase(leg.status()))
                .toList();
    }

    private static Map<UUID, String> causeByLeg(LegService legService, UUID tenantId,
                                                LocalDate from, LocalDate to) {
        Map<UUID, String> causes = new HashMap<>();
        for (LegDelayDto record : legService.findDelays(tenantId, from, to)) {
            causes.merge(record.legId(), record.cause(), (left, right) -> left + " · " + right);
        }
        return causes;
    }

    private static Map<String, AirportDto> airports(AirportService airportService,
                                                    List<LegDto> legs) {
        List<String> codes = legs.stream()
                .flatMap(leg -> java.util.stream.Stream.of(leg.depIcao(), leg.arrIcao()))
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
        return codes.isEmpty() ? Map.of() : airportService.findAllByIcao(codes);
    }

    /** Each sector counted twice: once where it left, once where it landed. */
    private static List<Rp.Bucket> movements(List<LegDto> legs) {
        Map<String, Double> counts = new LinkedHashMap<>();
        legs.forEach(leg -> {
            counts.merge(leg.depIcao(), 1d, Double::sum);
            counts.merge(leg.arrIcao(), 1d, Double::sum);
        });
        return counts.entrySet().stream()
                .map(entry -> new Rp.Bucket(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(Rp.Bucket::value).reversed())
                .toList();
    }

    private static Map<LocalDate, Double> dailyBlock(List<LegDto> legs) {
        Map<LocalDate, Double> daily = new HashMap<>();
        legs.forEach(leg -> daily.merge(leg.std().toLocalDate(), blockHours(leg), Double::sum));
        return daily;
    }

    private static long delayFreeDays(List<LegDto> legs, Map<UUID, Long> recorded) {
        Map<LocalDate, Boolean> byDay = new HashMap<>();
        legs.forEach(leg -> byDay.merge(leg.std().toLocalDate(), delay(leg, recorded) == 0,
                (left, right) -> left && right));
        return byDay.values().stream().filter(Boolean::booleanValue).count();
    }

    /** Delay minutes recorded against each leg by the OCC, summed per leg. */
    static Map<UUID, Long> recordedByLeg(LegService legService, UUID tenantId,
                                         LocalDate from, LocalDate to) {
        Map<UUID, Long> minutes = new HashMap<>();
        for (LegDelayDto record : legService.findDelays(tenantId, from, to)) {
            minutes.merge(record.legId(), (long) record.minutes(), Long::sum);
        }
        return minutes;
    }

    /**
     * The delay of a sector: the recorded off-block time, or the OCC delay
     * record, whichever is larger.
     *
     * <p>The two disagree more often than an operator expects. A sector held on
     * stand for forty minutes carries a delay record; if nobody entered the
     * actual OUT time, computing punctuality from the schedule alone would call
     * it on time. Taking the larger of the two is the prototype's rule and the
     * only one that cannot flatter the figure.
     */
    static long delay(LegDto leg, Map<UUID, Long> recorded) {
        return Math.max(delay(leg), recorded.getOrDefault(leg.id(), 0L));
    }

    private static long flyingDays(List<LegDto> legs) {
        return legs.stream().map(leg -> leg.std().toLocalDate()).distinct().count();
    }

    static double blockHours(LegDto leg) {
        return Rp.blockHours(leg.std(), leg.sta(), leg.outAt(), leg.inAt());
    }

    static long delay(LegDto leg) {
        return Rp.delayMinutes(leg.std(), leg.outAt());
    }

    static String route(LegDto leg) {
        return leg.depIcao() + "–" + leg.arrIcao();
    }

    /** Region names, as the aerodrome directory numbers them. */
    static String region(AirportDto airport) {
        if (airport == null || airport.region() == null) {
            return Rp.EMPTY;
        }
        return switch (airport.region()) {
            case 1 -> "Middle East";
            case 2 -> "Africa";
            case 3 -> "Europe";
            case 4 -> "Latin America";
            case 5 -> "Asia";
            case 6 -> "USA";
            case 7 -> "Canada";
            default -> Rp.EMPTY;
        };
    }

    private static String statusColour(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "SCHEDULED", "PLANNED" -> Rp.BLUE;
            case "AIRBORNE", "ENROUTE", "DEPARTED" -> Rp.GREEN;
            case "DELAYED" -> Rp.AMBER;
            case "LANDED", "ARRIVED", "CLOSED", "COMPLETED" -> Rp.NAVY2;
            case "CANCELLED" -> Rp.RED;
            default -> Rp.GREY;
        };
    }

    /** A cause long enough to be a sentence is cut: an axis is not a paragraph. */
    private static String shorten(String cause) {
        String trimmed = cause.replaceAll("\\s*\\(.*$", "").trim();
        return trimmed.length() > 42 ? trimmed.substring(0, 42) + "…" : trimmed;
    }
}
