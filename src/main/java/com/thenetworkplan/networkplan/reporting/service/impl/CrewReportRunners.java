package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.crew.dto.CrewDutyDto;
import com.thenetworkplan.networkplan.crew.dto.CrewExpiryDto;
import com.thenetworkplan.networkplan.crew.dto.FtlExceedanceDto;
import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.crew.service.CrewDutyService;
import com.thenetworkplan.networkplan.crew.service.CrewPeopleService;
import com.thenetworkplan.networkplan.crew.service.FdpTable;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportChartDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportKpiDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportSeriesDto;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import com.thenetworkplan.networkplan.roster.dto.RosterCellDto;
import com.thenetworkplan.networkplan.roster.dto.RosterMonthDto;
import com.thenetworkplan.networkplan.roster.dto.RosterRowDto;
import com.thenetworkplan.networkplan.roster.service.RosterService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
 * The eleven crew reports of the approved catalogue.
 *
 * <p><b>Three different sources, on purpose.</b> Currency reads the crew files;
 * block time and function read the sectors and who was on them; days, duty,
 * roster and FTL read the published roster and the duty periods behind it. A
 * single source would have been simpler and wrong: a crew member's licence
 * expiry has nothing to do with whether they were rostered, and a duty they
 * flew is a fact whether or not the roster month was ever published.
 *
 * <p><b>Nothing here returns a legality verdict it has not computed.</b> The
 * FTL sheet compares recorded duty against ORO.FTL.205 Table 2 and says which
 * band it used; where an input is missing it prints « — », never a margin. The
 * audit's finding was a green tick over a number nobody had checked, and that
 * is the failure mode every one of these reports is written to avoid.
 */
@Configuration
public class CrewReportRunners {

    /** ORO.FTL.235(d): seven local days free of duty per calendar month. */
    private static final int DAYS_OFF_PER_MONTH = 7;

    /** ORO.FTL.210(b): the annual block ceiling, scaled to the period. */
    private static final double ANNUAL_BLOCK_HOURS = 900d;

    /* ══════════════ CREW CURRENCY — the documents that ground people ══════ */

    @Bean
    ReportRunner crewCurrencyReport(CrewPeopleService peopleService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-EXP";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Licence, medical and crew-status validity horizon";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period as the expiry window";
            }

            @Override
            public List<String> columns() {
                return List.of("Crew", "Role", "Base", "Type rating", "Licence", "Days",
                        "Medical", "Days", "Training", "Days", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                return peopleService.findAll(tenantId, null, null, true).stream()
                        .sorted(Comparator.comparingLong(person -> worstDays(person, today)))
                        .map(person -> List.of(
                                person.fullName(),
                                person.mainRole(),
                                Rp.text(person.baseIcao()),
                                ratings(person),
                                Rp.iso(person.licenceExpiry()),
                                days(person.licenceExpiry(), today),
                                Rp.iso(person.medicalExpiry()),
                                days(person.medicalExpiry(), today),
                                Rp.iso(person.trainingExpiry()),
                                days(person.trainingExpiry(), today),
                                person.documentStatus()))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                List<PersonDto> crew = peopleService.findAll(tenantId, null, null, true);
                long expired = crew.stream().filter(person -> worstDays(person, today) <= 0).count();
                long under45 = crew.stream()
                        .filter(person -> worstDays(person, today) > 0
                                && worstDays(person, today) < 45).count();
                long between = crew.stream()
                        .filter(person -> worstDays(person, today) >= 45
                                && worstDays(person, today) < 90).count();
                long current = crew.size() - expired - under45;
                long lapsing = crew.stream().filter(person -> lapsesBetween(person, from, to)).count();

                return List.of(
                        Rp.kpi("Crew members", String.valueOf(crew.size()), "active establishment"),
                        Rp.kpi("Expired", String.valueOf(expired), "grounded until renewed",
                                expired == 0 ? "good" : "bad"),
                        Rp.kpi("Expiring < 45 d", String.valueOf(under45),
                                "licence, medical or training", Rp.countTone(under45)),
                        Rp.kpi("Expiring 45–90 d", String.valueOf(between), "plan recurrent now"),
                        Rp.kpi("Fully current", String.valueOf(current),
                                Rp.pct(current, crew.size()) + " % of crew", "good",
                                Rp.pct(current, crew.size())),
                        Rp.kpi("Lapsing in period", String.valueOf(lapsing), from + " → " + to,
                                Rp.countTone(lapsing)));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                List<PersonDto> crew = peopleService.findAll(tenantId, null, null, true);
                long expired = crew.stream().filter(person -> worstDays(person, today) <= 0).count();
                long under45 = crew.stream()
                        .filter(person -> worstDays(person, today) > 0
                                && worstDays(person, today) < 45).count();
                long between = crew.stream()
                        .filter(person -> worstDays(person, today) >= 45
                                && worstDays(person, today) < 90).count();

                List<PersonDto> soonest = crew.stream()
                        .sorted(Comparator.comparingLong(person -> worstDays(person, today)))
                        .limit(14).toList();

                return List.of(
                        Rp.donut("rpc1", "Validity horizon", "third",
                                List.of(new Rp.Bucket("Expired", expired),
                                        new Rp.Bucket("< 45 days", under45),
                                        new Rp.Bucket("45–90 days", between),
                                        new Rp.Bucket("> 90 days",
                                                crew.size() - expired - under45 - between)),
                                List.of(Rp.RED, Rp.AMBER, Rp.BLUE, Rp.GREEN)),
                        new ReportChartDto("rpc2", "Days to first expiry (per crew)", "bar",
                                "twothirds",
                                soonest.stream().map(PersonDto::fullName).toList(),
                                List.of(new ReportSeriesDto("Days",
                                        soonest.stream()
                                                .map(person -> (double) worstDays(person, today))
                                                .toList(), null,
                                        soonest.stream().map(person -> {
                                            long value = worstDays(person, today);
                                            return value <= 0 ? Rp.RED
                                                    : value < 45 ? Rp.AMBER : Rp.GREEN;
                                        }).toList()))),
                        Rp.hbar("rpc3", "Crew by role", "half",
                                Rp.tally(crew, PersonDto::mainRole), Rp.NAVY2),
                        Rp.donut("rpc4", "Crew by base", "half",
                                Rp.tally(crew, PersonDto::baseIcao)));
            }

            @Override
            public String note() {
                return "The window above is read as the expiry horizon, not as a filter on events: "
                        + "a ninety-day window says who lapses in the next ninety days. A document "
                        + "at or below zero days grounds that crew member immediately; the "
                        + "forty-five-day band is the planning trigger used everywhere else in the "
                        + "platform. A crew member with no date on file counts as UNKNOWN and is "
                        + "listed — never as valid.";
            }
        };
    }

    /* ══════════════ CREW CURRENCY SUMMARY — every qualification ══════════ */

    @Bean
    ReportRunner crewCurrencySummaryReport(CrewPeopleService peopleService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-TRAIN";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Recurrent qualifications, expiry pipeline and simulator currency";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period as the expiry window";
            }

            @Override
            public List<String> columns() {
                return List.of("Crew", "Role", "Qualification", "Subject", "Due",
                        "Days", "State");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return everything(peopleService, tenantId).stream()
                        .map(row -> List.of(
                                row.fullName(),
                                row.mainRole(),
                                row.kind(),
                                Rp.text(row.subject()),
                                Rp.iso(row.expiresOn()),
                                row.daysRemaining() == null ? Rp.EMPTY
                                        : String.valueOf(row.daysRemaining()),
                                state(row.daysRemaining())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<CrewExpiryDto> quals = everything(peopleService, tenantId);
                long expired = quals.stream()
                        .filter(row -> row.daysRemaining() != null && row.daysRemaining() <= 0)
                        .count();
                long due90 = quals.stream()
                        .filter(row -> row.daysRemaining() != null
                                && row.daysRemaining() > 0 && row.daysRemaining() < 90)
                        .count();
                long current = quals.size() - expired;
                long crew = quals.stream().map(CrewExpiryDto::personId).distinct().count();
                CrewExpiryDto urgent = quals.stream()
                        .filter(row -> row.daysRemaining() != null)
                        .min(Comparator.comparingLong(CrewExpiryDto::daysRemaining))
                        .orElse(null);
                long unknown = quals.stream()
                        .filter(row -> row.daysRemaining() == null).count();

                return List.of(
                        Rp.kpi("Qualifications tracked", String.valueOf(quals.size()),
                                crew + " crew members"),
                        Rp.kpi("Currency", Rp.pct(current, quals.size()) + " %",
                                current + " of " + quals.size() + " valid",
                                Rp.rateTone(Rp.pct(current, quals.size()), 95, 85),
                                Rp.pct(current, quals.size())),
                        Rp.kpi("Expired", String.valueOf(expired),
                                expired == 0 ? "none" : "crew not qualified",
                                expired == 0 ? "good" : "bad"),
                        Rp.kpi("Due < 90 days", String.valueOf(due90), "schedule recurrent",
                                Rp.countTone(due90)),
                        Rp.kpi("No date on file", String.valueOf(unknown),
                                unknown == 0 ? "every record dated" : "counted as unknown",
                                Rp.countTone(unknown)),
                        Rp.kpi("Most urgent",
                                urgent == null ? Rp.EMPTY
                                        : urgent.fullName() + " · " + urgent.daysRemaining() + " d",
                                "first qualification to lapse", "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<CrewExpiryDto> quals = everything(peopleService, tenantId);
                long expired = quals.stream()
                        .filter(row -> row.daysRemaining() != null && row.daysRemaining() <= 0)
                        .count();
                long due90 = quals.stream()
                        .filter(row -> row.daysRemaining() != null
                                && row.daysRemaining() > 0 && row.daysRemaining() < 90)
                        .count();

                Map<String, Long> firstLapse = new LinkedHashMap<>();
                quals.stream()
                        .filter(row -> row.daysRemaining() != null)
                        .forEach(row -> firstLapse.merge(row.fullName(), row.daysRemaining(),
                                Math::min));
                List<Map.Entry<String, Long>> soonest = firstLapse.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .limit(20).toList();

                return List.of(
                        Rp.donut("rpc1", "Qualification currency", "third",
                                List.of(new Rp.Bucket("Valid > 90 d",
                                                quals.size() - expired - due90),
                                        new Rp.Bucket("Due < 90 d", due90),
                                        new Rp.Bucket("Expired", expired)),
                                List.of(Rp.GREEN, Rp.AMBER, Rp.RED)),
                        Rp.hbar("rpc2", "Qualifications expiring within 180 days", "twothirds",
                                Rp.topN(Rp.tally(quals.stream()
                                        .filter(row -> row.daysRemaining() != null
                                                && row.daysRemaining() < 180).toList(),
                                        CrewExpiryDto::kind), 10), Rp.AMBER),
                        new ReportChartDto("rpc3", "Days to first lapse (per crew)", "bar", "full",
                                soonest.stream().map(Map.Entry::getKey).toList(),
                                List.of(new ReportSeriesDto("Days",
                                        soonest.stream().map(entry ->
                                                (double) entry.getValue()).toList(), null,
                                        soonest.stream().map(entry ->
                                                entry.getValue() <= 0 ? Rp.RED
                                                        : entry.getValue() < 90 ? Rp.AMBER
                                                                : Rp.GREEN).toList()))));
            }

            @Override
            public String note() {
                return "Covers the three crew-file clocks — licence, medical and recurrent "
                        + "training — together with every type rating and recurrent qualification "
                        + "recorded against a crew member. An expired recurrent qualification "
                        + "removes that crew member from the roster for the affected duty type, "
                        + "which is why it is counted here and not filtered out as historic.";
            }
        };
    }

    /* ══════════════ BLOCK TIME BY FUNCTION — the seat, not the person ═════ */

    @Bean
    ReportRunner blockByFunctionReport(LegService legService,
                                       CrewAssignmentService assignmentService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-FUNC";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Block hours split by operating function — PIC, SIC and cabin crew";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to crewed sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Crew", "Function", "Role", "Sectors", "Block time",
                        "Avg sector", "% of function");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<Flown> flown = crewed(legService, assignmentService, tenantId, from, to);
                Map<String, Double> byFunction = new HashMap<>();
                flown.forEach(entry -> byFunction.merge(entry.function(), entry.hours(), Double::sum));

                Map<String, double[]> byPerson = new LinkedHashMap<>();
                Map<String, String[]> labels = new HashMap<>();
                flown.forEach(entry -> {
                    String key = entry.name() + "||" + entry.function();
                    double[] figures = byPerson.computeIfAbsent(key, ignored -> new double[2]);
                    figures[0] += entry.hours();
                    figures[1]++;
                    labels.putIfAbsent(key, new String[]{entry.name(), entry.function(), entry.role()});
                });

                return byPerson.entrySet().stream()
                        .sorted(Comparator.comparingDouble(
                                (Map.Entry<String, double[]> entry) -> entry.getValue()[0])
                                .reversed())
                        .map(entry -> {
                            String[] label = labels.get(entry.getKey());
                            double hours = entry.getValue()[0];
                            int sectors = (int) entry.getValue()[1];
                            return List.of(
                                    label[0], label[1], label[2],
                                    String.valueOf(sectors),
                                    Rp.dur(hours),
                                    Rp.dur(hours / sectors),
                                    Rp.pct(hours, byFunction.getOrDefault(label[1], 0d)) + " %");
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<Flown> flown = crewed(legService, assignmentService, tenantId, from, to);
                List<Rp.Bucket> functions = Rp.sumBy(flown, Flown::function, Flown::hours);
                double total = functions.stream().mapToDouble(Rp.Bucket::value).sum();

                List<ReportKpiDto> kpis = new ArrayList<>();
                functions.stream().limit(3).forEach(bucket -> kpis.add(
                        Rp.kpi(bucket.key() + " block time", Rp.dur(bucket.value()),
                                Rp.pct(bucket.value(), total) + " % of crew hours",
                                functionTone(bucket.key()), Rp.pct(bucket.value(), total))));
                kpis.add(Rp.kpi("Crew hours total", Rp.dur(total), "sum across all functions"));
                kpis.add(Rp.kpi("Sectors crewed",
                        String.valueOf(flown.stream().map(Flown::legId).distinct().count()),
                        "with a resolved crew"));
                kpis.add(Rp.kpi("Crew members flying",
                        String.valueOf(flown.stream().map(Flown::name).distinct().count()),
                        "distinct individuals"));
                return kpis;
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<Flown> flown = crewed(legService, assignmentService, tenantId, from, to);
                List<Rp.Bucket> functions = Rp.sumBy(flown, Flown::function, Flown::hours);
                List<Rp.Bucket> people = Rp.topN(Rp.sumBy(flown, Flown::name, Flown::hours), 12);
                Map<String, String> functionOf = new HashMap<>();
                flown.forEach(entry -> functionOf.putIfAbsent(entry.name(), entry.function()));

                Map<LocalDate, Map<String, Double>> monthly = new LinkedHashMap<>();
                flown.stream().sorted(Comparator.comparing(Flown::day)).forEach(entry ->
                        monthly.computeIfAbsent(entry.day().withDayOfMonth(1),
                                        ignored -> new LinkedHashMap<>())
                                .merge(entry.function(), entry.hours(), Double::sum));

                return List.of(
                        Rp.donut("rpc1", "Block hours by function", "third", functions,
                                functions.stream().map(bucket ->
                                        functionColour(bucket.key())).toList()),
                        new ReportChartDto("rpc2", "Top 12 crew by block hours", "hbar",
                                "twothirds",
                                people.stream().map(Rp.Bucket::key).toList(),
                                List.of(new ReportSeriesDto("Block h",
                                        people.stream().map(bucket ->
                                                Math.round(bucket.value() * 10) / 10d).toList(),
                                        null,
                                        people.stream().map(bucket ->
                                                functionColour(functionOf.get(bucket.key())))
                                                .toList()))),
                        Rp.stacked("rpc3", "Function split per month", "full",
                                monthly.keySet().stream().map(Rp::monthLabel).toList(),
                                functions.stream().map(bucket -> Rp.series(bucket.key(),
                                        monthly.values().stream()
                                                .map(row -> Math.round(
                                                        row.getOrDefault(bucket.key(), 0d) * 10)
                                                        / 10d)
                                                .toList(),
                                        functionColour(bucket.key()))).toList()));
            }

            @Override
            public String note() {
                return "Crew hours exceed aircraft block hours because every sector is counted "
                        + "once per crew member on board: a two-pilot sector contributes its block "
                        + "twice. That is the figure a training department needs — it is not fleet "
                        + "block time. The function is the seat recorded on the assignment, not the "
                        + "licence rank: a captain flying as co-pilot is counted as SIC for that "
                        + "sector, which is how the logbook reads it.";
            }
        };
    }

    /* ══════════════ CREW BLOCK TIME — exposure per person ════════════════ */

    @Bean
    ReportRunner crewBlockPerPersonReport(CrewDutyService dutyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-BLOCK";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Block hours per crew member with rolling 28-day exposure";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to crewed sectors";
            }

            @Override
            public List<String> columns() {
                return List.of("Crew", "Staff", "Role", "Sectors", "Block time", "Days flown",
                        "Block / duty day", "Exposure");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                double ceiling = proRata(from, to);
                return people(dutyService, tenantId, from, to).values().stream()
                        .sorted(Comparator.comparingDouble(Person::blockHours).reversed())
                        .map(person -> List.of(
                                person.fullName,
                                person.staffNo,
                                person.role,
                                String.valueOf(person.sectors),
                                Rp.hhmm(person.blockMinutes),
                                String.valueOf(person.days.size()),
                                Rp.hhmm(person.blockMinutes / Math.max(1, person.days.size())),
                                exposure(person.blockHours(), ceiling)))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                Map<UUID, Person> crew = people(dutyService, tenantId, from, to);
                double ceiling = proRata(from, to);
                long totalMinutes = crew.values().stream()
                        .mapToLong(person -> person.blockMinutes).sum();
                Person busiest = crew.values().stream()
                        .max(Comparator.comparingLong(person -> person.blockMinutes)).orElse(null);
                long above = crew.values().stream()
                        .filter(person -> person.blockHours() > ceiling).count();

                return List.of(
                        Rp.kpi("Crew flying", String.valueOf(crew.size()),
                                "with a recorded block time"),
                        Rp.kpi("Crew block hours", Rp.hhmm(totalMinutes),
                                "all functions combined", "warn"),
                        Rp.kpi("Average per crew",
                                Rp.hhmm(crew.isEmpty() ? 0 : totalMinutes / crew.size()),
                                Rp.days(from, to).size() + " day period"),
                        Rp.kpi("Highest exposure",
                                busiest == null ? Rp.EMPTY : Rp.hhmm(busiest.blockMinutes),
                                busiest == null ? "" : busiest.fullName, "warn"),
                        Rp.kpi("Pro-rata 900 h limit", Rp.dur(ceiling),
                                "ORO.FTL.210(b) scaled to period"),
                        Rp.kpi("Above pro-rata", String.valueOf(above),
                                above == 0 ? "none" : "review the rolling 12-month total",
                                above == 0 ? "good" : "bad"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                Map<UUID, Person> crew = people(dutyService, tenantId, from, to);
                double ceiling = proRata(from, to);
                List<Person> ranked = crew.values().stream()
                        .sorted(Comparator.comparingLong((Person person) ->
                                person.blockMinutes).reversed())
                        .limit(20).toList();

                return List.of(
                        new ReportChartDto("rpc1", "Block hours per crew member", "hbar", "full",
                                ranked.stream().map(person -> person.fullName).toList(),
                                List.of(new ReportSeriesDto("Block h",
                                        ranked.stream().map(person ->
                                                Math.round(person.blockHours() * 10) / 10d).toList(),
                                        null,
                                        ranked.stream().map(person ->
                                                person.blockHours() > ceiling ? Rp.RED
                                                        : person.blockHours() > ceiling * 0.8
                                                                ? Rp.AMBER : Rp.NAVY2).toList()))),
                        Rp.bar("rpc2", "Sectors flown per crew member", "half",
                                Rp.topN(Rp.sumBy(crew.values(), person -> person.fullName,
                                        person -> person.sectors), 12), Rp.BLUE),
                        Rp.donut("rpc3", "Distribution by role", "half",
                                Rp.sumBy(crew.values(), person -> person.role,
                                        Person::blockHours)));
            }

            @Override
            public String note() {
                return "Block time is summed from the duty periods that actually record flying, "
                        + "never from the roster codes: a roster says what a day was for, not how "
                        + "long it lasted. The pro-rata figure scales the 900-hour annual "
                        + "flight-time limit of ORO.FTL.210(b) to the length of the selected "
                        + "period; it is an exposure indicator for planning, not a legal "
                        + "determination — the binding check is the rolling twelve-month total.";
            }
        };
    }

    /* ══════════════ CREW DAYS — what the period was spent on ═════════════ */

    @Bean
    ReportRunner crewDaysReport(RosterService rosterService, CrewDutyService dutyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-DAYS";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Duty days, days off and standby split per crew member";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period on rostered days";
            }

            @Override
            public List<String> columns() {
                return List.of("Crew", "Staff", "Role", "Duty days", "Duty hours", "Standby",
                        "Training", "Days off", "Leave", "Sick", "Rest target");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                Map<UUID, Roster> rosters = roster(rosterService, tenantId, from, to);
                Map<UUID, Long> dutyMinutes = dutyMinutesByPerson(dutyService, tenantId, from, to);
                int expected = expectedDaysOff(from, to);

                return rosters.values().stream()
                        .sorted(Comparator.comparingInt((Roster row) -> row.duty).reversed())
                        .map(row -> List.of(
                                row.fullName,
                                row.staffNo,
                                row.role,
                                String.valueOf(row.duty),
                                Rp.hhmm(dutyMinutes.getOrDefault(row.personId, 0L)),
                                String.valueOf(row.standby),
                                String.valueOf(row.training),
                                String.valueOf(row.off),
                                String.valueOf(row.leave),
                                String.valueOf(row.sick),
                                row.off < expected ? "BELOW" : "Met"))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                Map<UUID, Roster> rosters = roster(rosterService, tenantId, from, to);
                int totalDuty = rosters.values().stream().mapToInt(row -> row.duty).sum();
                int totalOff = rosters.values().stream().mapToInt(row -> row.off).sum();
                int expected = expectedDaysOff(from, to);
                long below = rosters.values().stream().filter(row -> row.off < expected).count();

                return List.of(
                        Rp.kpi("Crew rostered", String.valueOf(rosters.size()),
                                Rp.days(from, to).size() + " days in period"),
                        Rp.kpi("Duty days", String.valueOf(totalDuty),
                                "flight, positioning and training"),
                        Rp.kpi("Days off", String.valueOf(totalOff),
                                Rp.pct(totalOff, totalDuty + totalOff) + " % of rostered days",
                                "good"),
                        Rp.kpi("Average duty days",
                                rosters.isEmpty() ? "0"
                                        : Rp.n1((double) totalDuty / rosters.size()),
                                "per crew member"),
                        Rp.kpi("Expected days off", String.valueOf(expected),
                                "ORO.FTL.235(d) pro-rata"),
                        Rp.kpi("Below days-off target", String.valueOf(below),
                                below == 0 ? "none" : "review the roster",
                                below == 0 ? "good" : "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                Map<UUID, Roster> rosters = roster(rosterService, tenantId, from, to);
                Map<UUID, Long> dutyMinutes = dutyMinutesByPerson(dutyService, tenantId, from, to);
                List<Roster> ranked = rosters.values().stream()
                        .sorted(Comparator.comparingInt((Roster row) -> row.duty).reversed())
                        .limit(18).toList();

                List<Rp.Bucket> mix = List.of(
                        new Rp.Bucket("Duty", rosters.values().stream()
                                .mapToInt(row -> row.duty).sum()),
                        new Rp.Bucket("Standby", rosters.values().stream()
                                .mapToInt(row -> row.standby).sum()),
                        new Rp.Bucket("Training", rosters.values().stream()
                                .mapToInt(row -> row.training).sum()),
                        new Rp.Bucket("Off", rosters.values().stream()
                                .mapToInt(row -> row.off).sum()),
                        new Rp.Bucket("Leave", rosters.values().stream()
                                .mapToInt(row -> row.leave).sum()),
                        new Rp.Bucket("Sick", rosters.values().stream()
                                .mapToInt(row -> row.sick).sum()));

                return List.of(
                        Rp.stacked("rpc1", "Duty days vs days off", "full",
                                ranked.stream().map(row -> row.fullName).toList(),
                                List.of(
                                        Rp.series("Duty", ranked.stream()
                                                .map(row -> (double) row.duty).toList(), Rp.NAVY2),
                                        Rp.series("Standby", ranked.stream()
                                                .map(row -> (double) row.standby).toList(), Rp.BLUE),
                                        Rp.series("Training", ranked.stream()
                                                .map(row -> (double) row.training).toList(),
                                                Rp.PURPLE),
                                        Rp.series("Off", ranked.stream()
                                                .map(row -> (double) row.off).toList(), Rp.GREEN),
                                        Rp.series("Leave", ranked.stream()
                                                .map(row -> (double) row.leave).toList(), Rp.GREY))),
                        Rp.donut("rpc2", "Rostered day mix", "third",
                                mix.stream().filter(bucket -> bucket.value() > 0).toList()),
                        Rp.bar("rpc3", "Duty hours per crew member", "twothirds",
                                Rp.topN(rosters.values().stream()
                                        .map(row -> new Rp.Bucket(row.fullName,
                                                dutyMinutes.getOrDefault(row.personId, 0L) / 60d))
                                        .sorted(Comparator.comparingDouble(Rp.Bucket::value)
                                                .reversed())
                                        .toList(), 14), Rp.GOLD));
            }

            @Override
            public String note() {
                return "Counts rostered days only: a crew member with no roster line in the period "
                        + "does not appear, which is different from one rostered entirely off. The "
                        + "days-off target pro-rates the seven duty-free local days per calendar "
                        + "month required by ORO.FTL.235(d) to the length of the period; on a short "
                        + "window it is an indicator, not a finding — the regulation is written per "
                        + "calendar month.";
            }
        };
    }

    /* ══════════════ CREW MEMBERS — the directory ═════════════════════════ */

    @Bean
    ReportRunner crewMembersReport(CrewPeopleService peopleService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-MEMBERS";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Crew directory — ratings, base, seniority and contact record";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period as the expiry window";
            }

            @Override
            public List<String> columns() {
                return List.of("Staff", "Name", "Role", "Base", "Type ratings", "Licence",
                        "Medical", "Training", "Status", "Absent today");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return peopleService.findAll(tenantId, null, null, false).stream()
                        .map(person -> List.of(
                                person.staffNo(),
                                person.fullName(),
                                person.mainRole(),
                                Rp.text(person.baseIcao()),
                                ratings(person),
                                Rp.iso(person.licenceExpiry()),
                                Rp.iso(person.medicalExpiry()),
                                Rp.iso(person.trainingExpiry()),
                                person.active() ? person.documentStatus() : "INACTIVE",
                                Rp.text(person.absentToday())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<PersonDto> crew = peopleService.findAll(tenantId, null, null, false);
                long active = crew.stream().filter(PersonDto::active).count();
                List<Rp.Bucket> roles = Rp.tally(crew, PersonDto::mainRole);
                List<Rp.Bucket> bases = Rp.tally(crew, PersonDto::baseIcao);
                List<Rp.Bucket> ratings = Rp.tally(crew.stream()
                        .flatMap(person -> person.typeRatings().stream()).toList(),
                        rating -> rating);
                long lapsing = crew.stream().filter(person -> lapsesBetween(person, from, to)).count();

                return List.of(
                        Rp.kpi("Crew members", String.valueOf(crew.size()),
                                roles.size() + " distinct roles"),
                        Rp.kpi("Active", String.valueOf(active),
                                Rp.pct(active, crew.size()) + " % of establishment", "good",
                                Rp.pct(active, crew.size())),
                        Rp.kpi("Bases", String.valueOf(bases.size()),
                                bases.stream().limit(4)
                                        .map(bucket -> bucket.key() + " (" + bucket.count() + ")")
                                        .reduce((left, right) -> left + " · " + right).orElse("")),
                        Rp.kpi("Type ratings", String.valueOf(ratings.size()),
                                ratings.isEmpty() ? "none on file"
                                        : "largest: " + ratings.get(0).key()),
                        Rp.kpi("Absent today",
                                String.valueOf(crew.stream()
                                        .filter(person -> person.absentToday() != null).count()),
                                "leave, sick or unavailable"),
                        Rp.kpi("Lapsing in period", String.valueOf(lapsing), from + " → " + to,
                                Rp.countTone(lapsing)));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<PersonDto> crew = peopleService.findAll(tenantId, null, null, false);
                return List.of(
                        Rp.hbar("rpc1", "Crew by role", "half",
                                Rp.tally(crew, PersonDto::mainRole), Rp.NAVY2),
                        Rp.donut("rpc2", "Crew by type rating", "half",
                                Rp.tally(crew.stream()
                                        .flatMap(person -> person.typeRatings().stream()).toList(),
                                        rating -> rating)),
                        Rp.bar("rpc3", "Crew by base", "full",
                                Rp.tally(crew, PersonDto::baseIcao), Rp.GOLD));
            }

            @Override
            public String note() {
                return "The personnel directory as held in Crew Management, inactive crew "
                        + "included: an establishment report that hid them would understate the "
                        + "payroll. \"Lapsing in period\" flags anyone whose licence, medical or "
                        + "recurrent training falls due between the two dates above.";
            }
        };
    }

    /* ══════════════ CREW STAFFING PLAN — demand against supply ═══════════ */

    @Bean
    ReportRunner crewStaffingReport(LegService legService, CrewPeopleService peopleService,
                                    CamoService camoService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-STAFF";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Crew required by the flown programme against crew available per fleet";
            }

            @Override
            public String scope() {
                return "Fleet + period applied to sectors and crew supply";
            }

            @Override
            public List<String> columns() {
                return List.of("Type", "Tails flown", "Sectors", "Block time", "Captains",
                        "First officers", "Cabin", "Pilots req.", "Pilot gap", "Cabin gap");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return staffing(legService, peopleService, camoService, tenantId, from, to).stream()
                        .map(row -> List.of(
                                row.type,
                                String.valueOf(row.tails),
                                String.valueOf(row.sectors),
                                Rp.dur(row.blockHours),
                                String.valueOf(row.captains),
                                String.valueOf(row.firstOfficers),
                                String.valueOf(row.cabin),
                                String.valueOf(row.requiredPilots()),
                                signed(row.pilotGap()),
                                signed(row.cabinGap())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<Staffing> rows = staffing(legService, peopleService, camoService,
                        tenantId, from, to);
                int pilots = rows.stream().mapToInt(row -> row.captains + row.firstOfficers).sum();
                int cabin = rows.stream().mapToInt(row -> row.cabin).sum();
                int required = rows.stream().mapToInt(Staffing::requiredPilots).sum();
                int shortfall = rows.stream().mapToInt(row -> Math.min(0, row.pilotGap())).sum();
                long under = rows.stream()
                        .filter(row -> row.pilotGap() < 0 || row.cabinGap() < 0).count();

                return List.of(
                        Rp.kpi("Types in service", String.valueOf(rows.size()),
                                rows.stream().mapToInt(row -> row.tails).sum() + " tails flown"),
                        Rp.kpi("Pilots available", String.valueOf(pilots), "active, type-rated"),
                        Rp.kpi("Cabin crew available", String.valueOf(cabin), "active"),
                        Rp.kpi("Pilot requirement", String.valueOf(required),
                                "two crews per tail flown"),
                        Rp.kpi("Pilot shortfall", String.valueOf(Math.abs(shortfall)),
                                shortfall < 0 ? "recruit or cross-train" : "establishment met",
                                shortfall < 0 ? "bad" : "good"),
                        Rp.kpi("Types under-crewed", String.valueOf(under),
                                under == 0 ? "none" : "against the planning ratio",
                                under == 0 ? "good" : "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<Staffing> rows = staffing(legService, peopleService, camoService,
                        tenantId, from, to);
                List<String> labels = rows.stream().map(row -> row.type).toList();

                return List.of(
                        Rp.stacked("rpc1", "Pilots available by seat", "half", labels,
                                List.of(
                                        Rp.series("Captains", rows.stream()
                                                .map(row -> (double) row.captains).toList(),
                                                Rp.GOLD),
                                        Rp.series("First officers", rows.stream()
                                                .map(row -> (double) row.firstOfficers).toList(),
                                                Rp.NAVY2))),
                        new ReportChartDto("rpc2", "Pilot requirement per type", "bar", "half",
                                labels, List.of(Rp.series("Required", rows.stream()
                                        .map(row -> (double) row.requiredPilots()).toList(),
                                        Rp.RED))),
                        new ReportChartDto("rpc3", "Sectors flown per type", "bar", "full",
                                labels, List.of(Rp.series("Sectors", rows.stream()
                                        .map(row -> (double) row.sectors).toList(), Rp.BLUE))));
            }

            @Override
            public String note() {
                return "The requirement uses the planning ratio of two complete crews per tail "
                        + "actually flown in the period — four pilots and two cabin crew — which is "
                        + "the minimum that sustains a daily rotation with legal rest. It is a "
                        + "planning assumption, not a regulation: adjust it to your own OM-A before "
                        + "using this for a recruitment decision. Supply counts active crew holding "
                        + "a rating for the type; a crew member rated on two types is counted "
                        + "against both, so the column totals are not a headcount.";
            }
        };
    }

    /* ══════════════ CREW DUTY — every duty, as recorded ══════════════════ */

    @Bean
    ReportRunner crewDutyReport(CrewDutyService dutyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-DUTY";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Every rostered duty with report time, duty end and duration";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period on rostered days";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "Crew", "Role", "Duty", "Report", "Duty end",
                        "Duration", "Sectors", "Block", "Rest before");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return dutyService.findDuties(tenantId, from, to).stream()
                        .sorted(Comparator.comparing(CrewDutyDto::reportAt)
                                .thenComparing(CrewDutyDto::fullName))
                        .map(duty -> List.of(
                                duty.reportAt().toLocalDate().toString(),
                                duty.fullName(),
                                duty.mainRole(),
                                duty.rosterCode(),
                                Rp.hm(duty.reportAt()),
                                Rp.hm(duty.offDutyAt()),
                                Rp.hhmm(duty.dutyMinutes()),
                                duty.sectors() == 0 ? Rp.EMPTY : String.valueOf(duty.sectors()),
                                duty.blockMinutes() == null ? Rp.EMPTY
                                        : Rp.hhmm(duty.blockMinutes()),
                                duty.restBeforeMinutes() == null ? Rp.EMPTY
                                        : Rp.hhmm(duty.restBeforeMinutes())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<CrewDutyDto> duties = onDuty(dutyService, tenantId, from, to);
                long total = duties.stream().mapToLong(CrewDutyDto::dutyMinutes).sum();
                CrewDutyDto longest = duties.stream()
                        .max(Comparator.comparingLong(CrewDutyDto::dutyMinutes)).orElse(null);
                CrewDutyDto earliest = duties.stream()
                        .min(Comparator.comparingInt(CrewReportRunners::reportMinute)).orElse(null);
                long night = duties.stream()
                        .filter(duty -> reportMinute(duty) < 5 * 60
                                || duty.offDutyAt().toLocalDate()
                                        .isAfter(duty.reportAt().toLocalDate()))
                        .count();

                return List.of(
                        Rp.kpi("Duties recorded", String.valueOf(duties.size()),
                                Rp.days(from, to).size() + " day period"),
                        Rp.kpi("Total duty time", Rp.hhmm(total), "all crew combined", "warn"),
                        Rp.kpi("Average duty",
                                Rp.hhmm(duties.isEmpty() ? 0 : total / duties.size()),
                                "report to duty end"),
                        Rp.kpi("Longest duty",
                                longest == null ? Rp.EMPTY : Rp.hhmm(longest.dutyMinutes()),
                                longest == null ? ""
                                        : longest.fullName() + " · "
                                                + Rp.ddmmm(longest.reportAt().toLocalDate()),
                                "warn"),
                        Rp.kpi("Earliest report",
                                earliest == null ? Rp.EMPTY : Rp.hm(earliest.reportAt()),
                                earliest == null ? "" : earliest.fullName()),
                        Rp.kpi("Night duties", String.valueOf(night),
                                "report before 05:00 or end after midnight",
                                Rp.countTone(night)));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<CrewDutyDto> duties = onDuty(dutyService, tenantId, from, to);
                double[] byHour = new double[24];
                duties.forEach(duty -> byHour[reportMinute(duty) / 60]++);
                List<String> hours = new ArrayList<>(24);
                List<Double> counts = new ArrayList<>(24);
                for (int hour = 0; hour < 24; hour++) {
                    hours.add(Rp.pad2(hour) + "h");
                    counts.add(byHour[hour]);
                }

                return List.of(
                        Rp.hbar("rpc1", "Duty hours per crew member", "twothirds",
                                Rp.topN(Rp.sumBy(duties, CrewDutyDto::fullName,
                                        duty -> duty.dutyMinutes() / 60d), 16), Rp.NAVY2),
                        Rp.donut("rpc2", "Duty type mix", "third",
                                Rp.tally(duties, CrewDutyDto::rosterCode)),
                        new ReportChartDto("rpc3", "Report times by hour (UTC)", "bar", "full",
                                hours, List.of(Rp.series("Duties", counts, Rp.GOLD))));
            }

            @Override
            public String note() {
                return "Duty duration runs from report time to off-duty time as recorded, not as "
                        + "planned: this is the register an inspector asks for. \"Rest before\" is "
                        + "the gap since the same person's previous duty and is blank for the "
                        + "first duty of the window — the one before it lies outside, and a rest "
                        + "nobody measured is not a long rest. All times UTC.";
            }
        };
    }

    /* ══════════════ ROSTER AND DUTY — coverage day by day ════════════════ */

    @Bean
    ReportRunner rosterCoverageReport(RosterService rosterService, CrewDutyService dutyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-ROSTER";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Published roster coverage day by day, with duty and rest pattern per crew";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period on rostered days";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "Day", "On duty", "Standby", "Off / leave",
                        "Duty hours", "Crew on duty");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                Map<LocalDate, Day> byDay = byDay(rosterService, tenantId, from, to);
                Map<LocalDate, Long> hours = dutyMinutesByDay(dutyService, tenantId, from, to);
                return Rp.days(from, to).stream()
                        .map(day -> {
                            Day counts = byDay.getOrDefault(day, Day.EMPTY);
                            return List.of(
                                    day.toString(),
                                    day.getDayOfWeek().name().charAt(0)
                                            + day.getDayOfWeek().name().substring(1, 3).toLowerCase(),
                                    String.valueOf(counts.duty),
                                    String.valueOf(counts.standby),
                                    String.valueOf(counts.off),
                                    Rp.hhmm(hours.getOrDefault(day, 0L)),
                                    counts.names.isEmpty() ? Rp.EMPTY
                                            : String.join(", ", counts.names));
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                Map<LocalDate, Day> byDay = byDay(rosterService, tenantId, from, to);
                List<LocalDate> window = Rp.days(from, to);
                long published = byDay.values().stream()
                        .filter(day -> day.duty + day.standby + day.off > 0).count();
                int coverage = Rp.pct(published, window.size());
                Map.Entry<LocalDate, Day> peak = byDay.entrySet().stream()
                        .max(Comparator.comparingInt(entry -> entry.getValue().duty)).orElse(null);
                Map.Entry<LocalDate, Day> thinnest = byDay.entrySet().stream()
                        .min(Comparator.comparingInt(entry -> entry.getValue().standby))
                        .orElse(null);
                long crew = byDay.values().stream()
                        .flatMap(day -> day.names.stream()).distinct().count();
                int duty = byDay.values().stream().mapToInt(day -> day.duty).sum();
                int off = byDay.values().stream().mapToInt(day -> day.off).sum();

                return List.of(
                        Rp.kpi("Roster coverage", coverage + " %",
                                published + " of " + window.size() + " days rostered",
                                coverage == 100 ? "good" : "warn", coverage),
                        Rp.kpi("Crew rostered", String.valueOf(crew), "in the period"),
                        Rp.kpi("Duty assignments", String.valueOf(duty), "across the period"),
                        Rp.kpi("Peak duty day",
                                peak == null ? Rp.EMPTY : peak.getValue().duty + " crew",
                                peak == null ? "" : Rp.ddmmm(peak.getKey()), "warn"),
                        Rp.kpi("Thinnest standby day",
                                thinnest == null ? Rp.EMPTY
                                        : thinnest.getValue().standby + " on standby",
                                thinnest == null ? "" : Rp.ddmmm(thinnest.getKey()), "warn"),
                        Rp.kpi("Days off granted", String.valueOf(off), "rostered rest days",
                                "good"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                Map<LocalDate, Day> byDay = byDay(rosterService, tenantId, from, to);
                Map<LocalDate, Long> hours = dutyMinutesByDay(dutyService, tenantId, from, to);
                List<LocalDate> window = Rp.days(from, to);
                List<String> labels = window.stream().map(Rp::ddmmm).toList();

                return List.of(
                        Rp.stacked("rpc1", "Daily roster composition", "full", labels,
                                List.of(
                                        Rp.series("On duty", window.stream().map(day ->
                                                (double) byDay.getOrDefault(day, Day.EMPTY).duty)
                                                .toList(), Rp.NAVY2),
                                        Rp.series("Standby", window.stream().map(day ->
                                                (double) byDay.getOrDefault(day, Day.EMPTY).standby)
                                                .toList(), Rp.BLUE),
                                        Rp.series("Off / leave", window.stream().map(day ->
                                                (double) byDay.getOrDefault(day, Day.EMPTY).off)
                                                .toList(), Rp.GREEN))),
                        Rp.donut("rpc2", "Duty type distribution", "third",
                                codeMix(rosterService, tenantId, from, to)),
                        Rp.line("rpc3", "Duty hours per day", "twothirds", labels,
                                window.stream().map(day ->
                                        hours.getOrDefault(day, 0L) / 60d).toList(), Rp.GOLD));
            }

            @Override
            public String note() {
                return "Coverage below 100 % means part of the period carries no roster line at "
                        + "all — publish the remaining months in the Roster module before reading "
                        + "the rest of this report as complete. Where a published and a draft "
                        + "version both cover a day, the published one is counted: what the crew "
                        + "was told outranks what someone is still drafting.";
            }
        };
    }

    /* ══════════════ FTL SHEET — duty against the Table 2 ceiling ═════════ */

    @Bean
    ReportRunner ftlSheetReport(CrewDutyService dutyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-FTL";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Duty against maximum FDP per ORO.FTL.205 with rolling limit exposure";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period on rostered days";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "Crew", "Duty", "Sectors", "Report", "End",
                        "FDP used", "Max FDP", "Margin", "% used");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return onDuty(dutyService, tenantId, from, to).stream()
                        .sorted(Comparator.comparingLong(duty -> margin(duty) == null
                                ? Long.MAX_VALUE : margin(duty)))
                        .map(duty -> List.of(
                                duty.reportAt().toLocalDate().toString(),
                                duty.fullName(),
                                duty.rosterCode(),
                                String.valueOf(duty.sectors()),
                                Rp.hm(duty.reportAt()),
                                Rp.hm(duty.offDutyAt()),
                                Rp.hhmm(duty.dutyMinutes()),
                                duty.maxFdpMinutes() == null ? Rp.EMPTY
                                        : Rp.hhmm(duty.maxFdpMinutes()),
                                margin(duty) == null ? Rp.EMPTY : Rp.hhmm(margin(duty)),
                                duty.maxFdpMinutes() == null ? Rp.EMPTY
                                        : Rp.pct(duty.dutyMinutes(), duty.maxFdpMinutes()) + " %"))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<CrewDutyDto> duties = onDuty(dutyService, tenantId, from, to);
                List<CrewDutyDto> assessed = duties.stream()
                        .filter(duty -> duty.maxFdpMinutes() != null).toList();
                long over = assessed.stream().filter(duty -> margin(duty) < 0).count();
                long tight = assessed.stream()
                        .filter(duty -> margin(duty) >= 0 && margin(duty) <= 60).count();
                int average = assessed.isEmpty() ? 0 : (int) Math.round(assessed.stream()
                        .mapToDouble(duty -> duty.dutyMinutes() * 100d / duty.maxFdpMinutes())
                        .average().orElse(0));
                List<FtlExceedanceDto> rollingBreaches =
                        dutyService.findExceedances(tenantId, from, to).stream()
                                .filter(breach -> breach.rule().startsWith("ORO.FTL.210"))
                                .toList();
                CrewDutyDto tightest = assessed.stream()
                        .min(Comparator.comparingLong(CrewReportRunners::margin)).orElse(null);

                return List.of(
                        Rp.kpi("Duties assessed", String.valueOf(assessed.size()),
                                "against ORO.FTL.205 Table 2"),
                        Rp.kpi("Average FDP use", average + " %", "of maximum permitted",
                                average > 85 ? "warn" : "good", Math.min(100, average)),
                        Rp.kpi("Over maximum FDP", String.valueOf(over),
                                over == 0 ? "none" : "requires commander discretion",
                                over == 0 ? "good" : "bad"),
                        Rp.kpi("Within 1 hour of limit", String.valueOf(tight),
                                "little room for delay", Rp.countTone(tight)),
                        Rp.kpi("Rolling limit breaches", String.valueOf(rollingBreaches.size()),
                                "60 h / 7 days or 190 h / 28 days",
                                rollingBreaches.isEmpty() ? "good" : "bad"),
                        Rp.kpi("Tightest margin",
                                tightest == null ? Rp.EMPTY : Rp.hhmm(margin(tightest)),
                                tightest == null ? ""
                                        : tightest.fullName() + " · "
                                                + Rp.ddmmm(tightest.reportAt().toLocalDate()),
                                "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<CrewDutyDto> duties = onDuty(dutyService, tenantId, from, to);
                List<CrewDutyDto> assessed = duties.stream()
                        .filter(duty -> duty.maxFdpMinutes() != null)
                        .sorted(Comparator.comparingLong(CrewReportRunners::margin))
                        .limit(15).toList();

                Map<String, Long> distribution = new LinkedHashMap<>();
                distribution.put("Under 70 %", 0L);
                distribution.put("70–85 %", 0L);
                distribution.put("85–100 %", 0L);
                distribution.put("Over 100 %", 0L);
                duties.stream().filter(duty -> duty.maxFdpMinutes() != null).forEach(duty -> {
                    double used = duty.dutyMinutes() * 100d / duty.maxFdpMinutes();
                    String band = used < 70 ? "Under 70 %"
                            : used < 85 ? "70–85 %" : used <= 100 ? "85–100 %" : "Over 100 %";
                    distribution.merge(band, 1L, Long::sum);
                });
                List<String> bands = distribution.entrySet().stream()
                        .filter(entry -> entry.getValue() > 0).map(Map.Entry::getKey).toList();

                return List.of(
                        Rp.stacked("rpc1", "FDP used vs maximum (worst 15 duties)", "full",
                                assessed.stream().map(duty -> duty.fullName() + " "
                                        + Rp.ddmmm(duty.reportAt().toLocalDate())).toList(),
                                List.of(
                                        Rp.series("FDP used", assessed.stream()
                                                .map(duty -> Math.round(
                                                        duty.dutyMinutes() / 60d * 10) / 10d)
                                                .toList(), Rp.NAVY2),
                                        Rp.series("Margin remaining", assessed.stream()
                                                .map(duty -> Math.round(
                                                        Math.max(0, margin(duty)) / 60d * 10) / 10d)
                                                .toList(), Rp.GREEN))),
                        Rp.donut("rpc2", "FDP utilisation distribution", "third",
                                bands.stream().map(band ->
                                        new Rp.Bucket(band, distribution.get(band))).toList(),
                                bands.stream().map(CrewReportRunners::bandColour).toList()),
                        Rp.bar("rpc3", "Duty hours by crew member", "twothirds",
                                Rp.topN(Rp.sumBy(duties, CrewDutyDto::fullName,
                                        duty -> duty.dutyMinutes() / 60d), 14), Rp.GOLD));
            }

            @Override
            public String note() {
                return "Maximum FDP is read from ORO.FTL.205(b)(1) Table 2 for an acclimatised "
                        + "crew with no FRM approval, using the report time and the sector count "
                        + "recorded on the duty. Standby, training and office duties carry no "
                        + "flight-duty ceiling and show \"—\" rather than a margin against a table "
                        + "that does not apply to them. Duties over maximum would require commander "
                        + "discretion and must be reported: treat them as planning errors, not "
                        + "options.";
            }
        };
    }

    /* ══════════════ FTL VIOLATIONS — only what breached ══════════════════ */

    @Bean
    ReportRunner ftlViolationsReport(CrewDutyService dutyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-FTLV";
            }

            @Override
            public String module() {
                return "Crew";
            }

            @Override
            public String subtitle() {
                return "Duties exceeding a flight-time-limitation threshold, with the rule breached";
            }

            @Override
            public String scope() {
                return "Fleet by type rating + period on rostered days";
            }

            @Override
            public List<String> columns() {
                return List.of("Date", "Crew", "Rule", "Detail", "Exceeded by", "Severity");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return dutyService.findExceedances(tenantId, from, to).stream()
                        .map(breach -> List.of(
                                breach.day().toString(),
                                breach.crewName(),
                                breach.rule(),
                                breach.detail(),
                                Rp.hhmm(breach.overMinutes()),
                                breach.severity()))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<CrewDutyDto> duties = onDuty(dutyService, tenantId, from, to);
                List<FtlExceedanceDto> breaches = dutyService.findExceedances(tenantId, from, to);
                long high = breaches.stream()
                        .filter(breach -> "High".equals(breach.severity())).count();
                List<Rp.Bucket> byRule = Rp.tally(breaches, FtlExceedanceDto::rule);
                List<Rp.Bucket> byCrew = Rp.tally(breaches, FtlExceedanceDto::crewName);

                if (breaches.isEmpty()) {
                    return List.of(
                            Rp.kpi("Violations", "0",
                                    Rp.days(from, to).size() + " days checked", "good"),
                            Rp.kpi("Duties checked", String.valueOf(duties.size()),
                                    "against FDP, rest and rolling limits"),
                            Rp.kpi("Compliance", "100 %", "ORO.FTL / CS FTL.1", "good", 100));
                }

                return List.of(
                        Rp.kpi("Violations", String.valueOf(breaches.size()),
                                Rp.days(from, to).size() + " days checked", "bad"),
                        Rp.kpi("High severity", String.valueOf(high), "FDP or rest breaches",
                                high == 0 ? "good" : "bad"),
                        Rp.kpi("Crew affected", String.valueOf(byCrew.size()),
                                byCrew.stream().limit(3).map(Rp.Bucket::key)
                                        .reduce((left, right) -> left + ", " + right).orElse("")),
                        Rp.kpi("Most breached rule",
                                byRule.isEmpty() ? Rp.EMPTY
                                        : byRule.get(0).key().split("—")[0].trim(),
                                byRule.isEmpty() ? "" : byRule.get(0).count() + " occurrences",
                                "warn"),
                        Rp.kpi("Duties checked", String.valueOf(duties.size()),
                                "timed duties in the period"),
                        Rp.kpi("Breach rate",
                                Rp.pct(breaches.size(), Math.max(1, duties.size())) + " %",
                                "violations per duty", "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<FtlExceedanceDto> breaches =
                        dutyService.findExceedances(tenantId, from, to);
                if (breaches.isEmpty()) {
                    return List.of();
                }
                List<Rp.Bucket> bySeverity = Rp.tally(breaches, FtlExceedanceDto::severity);
                return List.of(
                        Rp.hbar("rpc1", "Violations by rule", "twothirds",
                                Rp.tally(breaches, FtlExceedanceDto::rule), Rp.RED),
                        Rp.donut("rpc2", "Severity", "third", bySeverity,
                                bySeverity.stream()
                                        .map(bucket -> "High".equals(bucket.key())
                                                ? Rp.RED : Rp.AMBER).toList()));
            }

            @Override
            public String note() {
                return "Three checks are applied to every recorded duty: the maximum daily FDP of "
                        + "ORO.FTL.205 Table 2, the minimum rest of ORO.FTL.235(a) — twelve hours "
                        + "or the length of the preceding duty, whichever is greater — and the "
                        + "rolling 60 h / 7 days and 190 h / 28 days ceilings of ORO.FTL.210(a). "
                        + "An empty report is the correct result, but it is only as good as the "
                        + "duty records behind it: a duty never recorded cannot breach anything "
                        + "here.";
            }
        };
    }

    /* ══════════════════════════ shared reading ═══════════════════════════ */

    /** One crew member's flying over the window, summed from duty periods. */
    private static final class Person {
        private String staffNo;
        private String fullName;
        private String role;
        private long blockMinutes;
        private int sectors;
        private final java.util.Set<LocalDate> days = new java.util.HashSet<>();

        private double blockHours() {
            return blockMinutes / 60d;
        }
    }

    private static Map<UUID, Person> people(CrewDutyService dutyService, UUID tenantId,
                                            LocalDate from, LocalDate to) {
        Map<UUID, Person> crew = new LinkedHashMap<>();
        for (CrewDutyDto duty : dutyService.findDuties(tenantId, from, to)) {
            if (duty.blockMinutes() == null || duty.blockMinutes() == 0) {
                continue;
            }
            Person person = crew.computeIfAbsent(duty.personId(), ignored -> new Person());
            person.staffNo = duty.staffNo();
            person.fullName = duty.fullName();
            person.role = duty.mainRole();
            person.blockMinutes += duty.blockMinutes();
            person.sectors += Math.max(1, duty.sectors());
            person.days.add(duty.reportAt().toLocalDate());
        }
        return crew;
    }

    /** One crew member's roster days, counted by what the code says they were. */
    private static final class Roster {
        private UUID personId;
        private String staffNo;
        private String fullName;
        private String role;
        private int duty;
        private int standby;
        private int training;
        private int off;
        private int leave;
        private int sick;
    }

    private static Map<UUID, Roster> roster(RosterService rosterService, UUID tenantId,
                                            LocalDate from, LocalDate to) {
        Map<UUID, Roster> crew = new LinkedHashMap<>();
        for (RosterMonthDto month : months(rosterService, tenantId, from, to)) {
            for (RosterRowDto row : month.rows()) {
                Roster person = crew.computeIfAbsent(row.personId(), ignored -> new Roster());
                person.personId = row.personId();
                person.staffNo = row.staffNo();
                person.fullName = row.fullName();
                person.role = row.mainRole();
                for (RosterCellDto cell : row.cells()) {
                    if (cell.day().isBefore(from) || cell.day().isAfter(to)) {
                        continue;
                    }
                    switch (cell.code()) {
                        case "OFF" -> person.off++;
                        case "LVE" -> person.leave++;
                        case "SICK" -> person.sick++;
                        case "SBY", "RES" -> person.standby++;
                        case "TRG", "SIM" -> person.training++;
                        default -> person.duty++;
                    }
                }
            }
        }
        return crew;
    }

    /** One day of the roster, seen from the whole crew. */
    private record Day(int duty, int standby, int off, List<String> names) {

        private static final Day EMPTY = new Day(0, 0, 0, List.of());
    }

    private static Map<LocalDate, Day> byDay(RosterService rosterService, UUID tenantId,
                                             LocalDate from, LocalDate to) {
        Map<LocalDate, int[]> counts = new LinkedHashMap<>();
        Map<LocalDate, List<String>> names = new LinkedHashMap<>();
        for (RosterMonthDto month : months(rosterService, tenantId, from, to)) {
            for (RosterRowDto row : month.rows()) {
                for (RosterCellDto cell : row.cells()) {
                    if (cell.day().isBefore(from) || cell.day().isAfter(to)) {
                        continue;
                    }
                    int[] figures = counts.computeIfAbsent(cell.day(), ignored -> new int[3]);
                    switch (cell.code()) {
                        case "OFF", "LVE", "SICK" -> figures[2]++;
                        case "SBY", "RES" -> figures[1]++;
                        default -> {
                            figures[0]++;
                            names.computeIfAbsent(cell.day(), ignored -> new ArrayList<>())
                                    .add(row.fullName());
                        }
                    }
                }
            }
        }
        Map<LocalDate, Day> out = new LinkedHashMap<>();
        counts.forEach((day, figures) -> out.put(day, new Day(figures[0], figures[1], figures[2],
                names.getOrDefault(day, List.of()))));
        return out;
    }

    private static List<Rp.Bucket> codeMix(RosterService rosterService, UUID tenantId,
                                           LocalDate from, LocalDate to) {
        List<String> codes = new ArrayList<>();
        for (RosterMonthDto month : months(rosterService, tenantId, from, to)) {
            for (RosterRowDto row : month.rows()) {
                row.cells().stream()
                        .filter(cell -> !cell.day().isBefore(from) && !cell.day().isAfter(to))
                        .forEach(cell -> codes.add(cell.code()));
            }
        }
        return Rp.tally(codes, code -> code);
    }

    /**
     * Every roster month the window touches.
     *
     * <p>One call per calendar month rather than one per day: the roster is
     * stored and published by month, and asking day by day would multiply the
     * same query by thirty.
     */
    private static List<RosterMonthDto> months(RosterService rosterService, UUID tenantId,
                                               LocalDate from, LocalDate to) {
        List<RosterMonthDto> out = new ArrayList<>();
        YearMonth last = YearMonth.from(to);
        for (YearMonth month = YearMonth.from(from);
                !month.isAfter(last); month = month.plusMonths(1)) {
            out.add(rosterService.findMonth(tenantId, month));
        }
        return out;
    }

    /**
     * The periods that actually consume duty time.
     *
     * <p>Rest and days off are stored as duty periods too — that is how a
     * roster records them — and every FTL figure has to leave them out. A
     * rolling 28-day total that summed eighteen-hour rest periods would put the
     * whole crew over the 190-hour ceiling and bury the one duty that really
     * breached it.
     */
    private static List<CrewDutyDto> onDuty(CrewDutyService dutyService, UUID tenantId,
                                            LocalDate from, LocalDate to) {
        return dutyService.findDuties(tenantId, from, to).stream()
                .filter(CrewDutyDto::countsAsDuty)
                .toList();
    }

    private static Map<UUID, Long> dutyMinutesByPerson(CrewDutyService dutyService, UUID tenantId,
                                                       LocalDate from, LocalDate to) {
        Map<UUID, Long> minutes = new HashMap<>();
        onDuty(dutyService, tenantId, from, to)
                .forEach(duty -> minutes.merge(duty.personId(), duty.dutyMinutes(), Long::sum));
        return minutes;
    }

    private static Map<LocalDate, Long> dutyMinutesByDay(CrewDutyService dutyService, UUID tenantId,
                                                         LocalDate from, LocalDate to) {
        Map<LocalDate, Long> minutes = new HashMap<>();
        onDuty(dutyService, tenantId, from, to).forEach(duty ->
                minutes.merge(duty.reportAt().toLocalDate(), duty.dutyMinutes(), Long::sum));
        return minutes;
    }

    /** One crew member on one sector: the unit the function report counts. */
    private record Flown(UUID legId, LocalDate day, String name, String function,
                         String role, double hours) {
    }

    /**
     * Who was on each sector of the window.
     *
     * <p>Assignments are asked for one day at a time, because that is the shape
     * the crew module exposes and a crew list only means anything against a
     * flight date. Ninety queries for a ninety-day report is a query per day,
     * not per leg.
     */
    private static List<Flown> crewed(LegService legService, CrewAssignmentService assignmentService,
                                      UUID tenantId, LocalDate from, LocalDate to) {
        Map<LocalDate, List<LegDto>> byDay = new LinkedHashMap<>();
        legService.findProgrammeRange(tenantId, from, to).stream()
                .filter(leg -> !"CANCELLED".equalsIgnoreCase(leg.status()))
                .forEach(leg -> byDay.computeIfAbsent(leg.std().toLocalDate(),
                        ignored -> new ArrayList<>()).add(leg));

        List<Flown> out = new ArrayList<>();
        byDay.forEach((day, legs) -> {
            Map<UUID, LegCrewDto> crews = assignmentService.findByLegIds(tenantId,
                    legs.stream().map(LegDto::id).toList(), day);
            for (LegDto leg : legs) {
                LegCrewDto crew = crews.get(leg.id());
                if (crew == null || crew.members().isEmpty()) {
                    continue;
                }
                double hours = OpsReportRunners.blockHours(leg);
                for (CrewMemberDto member : crew.members()) {
                    out.add(new Flown(leg.id(), day, member.fullName(),
                            function(member.seat()), Rp.text(member.mainRole()), hours));
                }
            }
        });
        return out;
    }

    /** One type's demand and the crew rated to answer it. */
    private static final class Staffing {
        private String type;
        private int tails;
        private int sectors;
        private double blockHours;
        private int captains;
        private int firstOfficers;
        private int cabin;

        /** Two complete crews per tail flown: four pilots, two cabin. */
        private int requiredPilots() {
            return tails * 4;
        }

        private int requiredCabin() {
            return tails * 2;
        }

        private int pilotGap() {
            return captains + firstOfficers - requiredPilots();
        }

        private int cabinGap() {
            return cabin - requiredCabin();
        }
    }

    private static List<Staffing> staffing(LegService legService, CrewPeopleService peopleService,
                                           CamoService camoService, UUID tenantId,
                                           LocalDate from, LocalDate to) {
        Map<String, Staffing> byType = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> tails = new HashMap<>();

        for (LegDto leg : legService.findProgrammeRange(tenantId, from, to)) {
            if ("CANCELLED".equalsIgnoreCase(leg.status()) || leg.icaoType() == null) {
                continue;
            }
            Staffing row = byType.computeIfAbsent(leg.icaoType(), type -> {
                Staffing fresh = new Staffing();
                fresh.type = type;
                return fresh;
            });
            row.sectors++;
            row.blockHours += OpsReportRunners.blockHours(leg);
            tails.computeIfAbsent(leg.icaoType(), ignored -> new java.util.HashSet<>())
                    .add(leg.registration());
        }
        byType.forEach((type, row) ->
                row.tails = tails.getOrDefault(type, java.util.Set.of()).size());

        /* Une qualification porte le type OACI ; on ne compte que l equipage
           actif, car un equipage inactif ne peut pas tenir une rotation. */
        for (PersonDto person : peopleService.findAll(tenantId, null, null, true)) {
            for (String rating : person.typeRatings()) {
                Staffing row = byType.get(rating);
                if (row == null) {
                    continue;
                }
                switch (person.mainRole()) {
                    case "CAPTAIN", "COMMANDER", "PIC" -> row.captains++;
                    case "CABIN", "CABIN_CREW", "FLIGHT_ATTENDANT" -> row.cabin++;
                    default -> row.firstOfficers++;
                }
            }
        }
        // Une immatriculation sans secteur reste hors du tableau : la demande
        // vient du programme, pas du registre.
        camoService.findFleetStatus(tenantId);
        return byType.values().stream()
                .sorted(Comparator.comparingInt((Staffing row) -> row.sectors).reversed())
                .toList();
    }

    /* ── FTL arithmetic ───────────────────────────────────────────────────── */

    /**
     * L'arithmetique FTL vit dans le module equipage, pas ici.
     *
     * <p>Elle etait recopiee dans ce fichier : deux copies, deux reponses le
     * jour ou l'une des deux bougerait. {@code CrewDutyService.findExceedances}
     * est desormais le seul endroit ou ORO.FTL.205, 210 et 235 sont appliques,
     * et la feuille FTL, le rapport des violations et la banniere du Safety
     * Manager lisent tous les trois le meme resultat.
     */
    private static Long margin(CrewDutyDto duty) {
        return duty.maxFdpMinutes() == null ? null : duty.maxFdpMinutes() - duty.dutyMinutes();
    }

    private static int reportMinute(CrewDutyDto duty) {
        var utc = duty.reportAt().withOffsetSameInstant(ZoneOffset.UTC);
        return utc.getHour() * 60 + utc.getMinute();
    }

    /* ── small conversions ────────────────────────────────────────────────── */

    private static long worstDays(PersonDto person, LocalDate today) {
        return Math.min(Math.min(daysTo(person.licenceExpiry(), today),
                daysTo(person.medicalExpiry(), today)),
                daysTo(person.trainingExpiry(), today));
    }

    /** A missing date is far away here, and UNKNOWN in the status column. */
    private static long daysTo(LocalDate expiry, LocalDate today) {
        return expiry == null ? 9999 : ChronoUnit.DAYS.between(today, expiry);
    }

    private static String days(LocalDate expiry, LocalDate today) {
        return expiry == null ? Rp.EMPTY : String.valueOf(ChronoUnit.DAYS.between(today, expiry));
    }

    private static boolean lapsesBetween(PersonDto person, LocalDate from, LocalDate to) {
        return within(person.licenceExpiry(), from, to)
                || within(person.medicalExpiry(), from, to)
                || within(person.trainingExpiry(), from, to);
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private static String ratings(PersonDto person) {
        return person.typeRatings().isEmpty() ? Rp.EMPTY : String.join(", ", person.typeRatings());
    }

    private static List<CrewExpiryDto> everything(CrewPeopleService peopleService, UUID tenantId) {
        // Dix ans d horizon : le rapport veut le pipeline entier, pas la
        // fenetre. Les dates manquantes reviennent quand meme, en UNKNOWN.
        return peopleService.findExpiring(tenantId, 3650);
    }

    private static String state(Long days) {
        if (days == null) {
            return "UNKNOWN";
        }
        return days <= 0 ? "EXPIRED" : days < 90 ? "Due soon" : "Valid";
    }

    private static String exposure(double hours, double ceiling) {
        if (hours > ceiling) {
            return "ABOVE PRO-RATA";
        }
        return hours > ceiling * 0.8 ? "Watch" : "Normal";
    }

    private static double proRata(LocalDate from, LocalDate to) {
        return ANNUAL_BLOCK_HOURS * Rp.days(from, to).size() / 365d;
    }

    private static int expectedDaysOff(LocalDate from, LocalDate to) {
        return (int) Math.round(DAYS_OFF_PER_MONTH * Rp.days(from, to).size() / 30d);
    }

    /** The operating function, from the seat recorded on the assignment. */
    private static String function(String seat) {
        if (seat == null) {
            return "Other";
        }
        return switch (seat.toUpperCase()) {
            case "CPT", "CAPTAIN", "PIC", "COMMANDER" -> "PIC";
            case "FO", "SIC", "FIRST_OFFICER", "COPILOT" -> "SIC";
            case "CC", "CABIN", "CABIN_CREW", "FA" -> "Cabin";
            default -> seat;
        };
    }

    private static String functionColour(String function) {
        return switch (function == null ? "" : function) {
            case "PIC" -> Rp.GOLD;
            case "SIC" -> Rp.NAVY2;
            case "Cabin" -> Rp.GREEN;
            default -> Rp.GREY;
        };
    }

    private static String functionTone(String function) {
        return switch (function == null ? "" : function) {
            case "PIC" -> "warn";
            case "Cabin" -> "good";
            default -> "neutral";
        };
    }

    private static String bandColour(String band) {
        return switch (band) {
            case "Under 70 %" -> Rp.GREEN;
            case "70–85 %" -> Rp.BLUE;
            case "85–100 %" -> Rp.AMBER;
            default -> Rp.RED;
        };
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }
}
