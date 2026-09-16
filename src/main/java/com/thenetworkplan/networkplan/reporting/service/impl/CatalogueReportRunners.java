package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.crew.service.CrewPeopleService;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportChartDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportKpiDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportSeriesDto;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import com.thenetworkplan.networkplan.safety.service.SafetyOverviewService;
import com.thenetworkplan.networkplan.safety.service.SmsRegisterService;
import com.thenetworkplan.networkplan.techlog.service.TechLogService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The crew, maintenance, fleet and safety reports of the approved catalogue.
 *
 * <p>Each runner asks the owning module's service. None of them reads another
 * schema: a crew report that went straight to {@code crew.duty_periods} would
 * be a second opinion about what a duty period is, and the two would diverge on
 * the first FTL amendment.
 */
@Configuration
public class CatalogueReportRunners {

    private static final String GOLD = "#a9811d";
    private static final String NAVY = "#1B2D6B";
    private static final String BLUE = "#2f6fb0";
    private static final String GREEN = "#1f9d5c";
    private static final String RED = "#C8202F";

    /* ── CREW — block time per person ─────────────────────────────────── */

    @Bean
    ReportRunner crewBlockPerPersonReport(CrewPeopleService peopleService) {
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
                return "Block hours flown by each crew member";
            }

            @Override
            public List<String> columns() {
                return List.of("Name", "Role", "Base", "Block 7 d", "Block 28 d",
                        "Block 365 d", "Type ratings");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return people(peopleService, tenantId).stream()
                        .sorted(Comparator.comparingLong(PersonDto::blockMinutes28d).reversed())
                        .map(person -> List.of(
                                person.fullName(),
                                titleish(person.mainRole()),
                                nullSafe(person.baseIcao()),
                                hhmm(person.blockMinutes7d()),
                                hhmm(person.blockMinutes28d()),
                                hhmm(person.blockMinutes365d()),
                                String.join(", ", person.typeRatings())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<PersonDto> people = people(peopleService, tenantId);
                long total = people.stream().mapToLong(PersonDto::blockMinutes28d).sum();
                PersonDto busiest = people.stream()
                        .max(Comparator.comparingLong(PersonDto::blockMinutes28d)).orElse(null);
                return List.of(
                        kpi("Crew on the register", String.valueOf(people.size()),
                                "active licence holders", "neutral"),
                        kpi("Block flown, 28 days", hhmm(total), "all crew together", "good"),
                        kpi("Average per head",
                                hhmm(people.isEmpty() ? 0 : total / people.size()),
                                "over 28 days", "neutral"),
                        kpi("Busiest", busiest == null ? "—" : busiest.fullName(),
                                busiest == null ? "no data" : hhmm(busiest.blockMinutes28d()),
                                "warn"));
            }

            @Override
            public String note() {
                return "The rolling windows are the ones the FTL scheme itself uses — 7, 28 and "
                        + "365 days — and they are maintained on those periods whatever window is "
                        + "chosen above. They are counters, not a query over the selected dates.";
            }
        };
    }

    /* ── CREW — block time by function ────────────────────────────────── */

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
                return "Block hours by the seat occupied, not by the person";
            }

            @Override
            public List<String> columns() {
                return List.of("Function", "Sectors crewed", "Block (h:mm)", "Share");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                Map<String, long[]> byFunction = new LinkedHashMap<>();
                long totalBlock = 0;

                for (LegDto leg : legService.findProgrammeRange(tenantId, from, to)) {
                    var crew = assignmentService.findByLeg(tenantId, leg.id(),
                            leg.std().atZoneSameInstant(ZoneOffset.UTC).toLocalDate());
                    if (crew == null) {
                        continue;
                    }
                    long block = blockMinutes(leg);
                    totalBlock += block;
                    for (var member : crew.members()) {
                        long[] cell = byFunction.computeIfAbsent(
                                nullSafe(member.seat()), key -> new long[2]);
                        cell[0]++;
                        cell[1] += block;
                    }
                }

                long finalTotal = totalBlock;
                return byFunction.entrySet().stream()
                        .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(
                                entry -> entry.getValue()[1]).reversed())
                        .map(entry -> List.of(
                                titleish(entry.getKey()),
                                String.valueOf(entry.getValue()[0]),
                                hhmm(entry.getValue()[1]),
                                finalTotal == 0 ? "—"
                                        : Math.round(entry.getValue()[1] * 100f / finalTotal) + "%"))
                        .toList();
            }

            @Override
            public String note() {
                return "Block time is counted once per seat, so the total across functions is a "
                        + "multiple of the sector block time — a two-crew sector contributes its "
                        + "block twice. That is the figure a training department needs; it is not "
                        + "fleet block time.";
            }
        };
    }

    /* ── CREW — the register ──────────────────────────────────────────── */

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
                return "The register, with the qualification that expires first";
            }

            @Override
            public List<String> columns() {
                return List.of("Staff no", "Name", "Role", "Base", "Licence", "Medical",
                        "Training", "Document status", "Type ratings", "Active");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return peopleService.findAll(tenantId, null, null, false).stream()
                        .map(person -> List.of(
                                nullSafe(person.staffNo()),
                                person.fullName(),
                                titleish(person.mainRole()),
                                nullSafe(person.baseIcao()),
                                nullSafe(person.licenceExpiry()),
                                nullSafe(person.medicalExpiry()),
                                nullSafe(person.trainingExpiry()),
                                nullSafe(person.documentStatus()),
                                String.join(", ", person.typeRatings()),
                                person.active() ? "Yes" : "No"))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<PersonDto> all = peopleService.findAll(tenantId, null, null, false);
                long expired = all.stream()
                        .filter(person -> "EXPIRED".equals(person.documentStatus())).count();
                long expiring = all.stream()
                        .filter(person -> "EXPIRING".equals(person.documentStatus())).count();
                return List.of(
                        kpi("On the register", String.valueOf(all.size()), "including inactive",
                                "neutral"),
                        kpi("Not current", String.valueOf(expired),
                                expired == 0 ? "none" : "must not be rostered",
                                expired == 0 ? "good" : "bad"),
                        kpi("Expiring", String.valueOf(expiring), "within the alert window",
                                expiring == 0 ? "good" : "warn"),
                        kpi("Active", String.valueOf(all.stream().filter(PersonDto::active).count()),
                                "available to roster", "good"));
            }

            @Override
            public String note() {
                return "Document status is the worst of the three clocks — licence, medical and "
                        + "recurrent training — because the earliest of them is what stops the "
                        + "person flying.";
            }
        };
    }

    /* ── CREW — expiries with days remaining ──────────────────────────── */

    @Bean
    ReportRunner crewDaysReport(CrewPeopleService peopleService) {
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
                return "Days to the next expiry, per crew member";
            }

            @Override
            public List<String> columns() {
                return List.of("Name", "Role", "Document", "Expires", "Days remaining", "State");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                int horizon = (int) Math.max(1, from.datesUntil(to.plusDays(1)).count());
                return peopleService.findExpiring(tenantId, horizon).stream()
                        .map(expiry -> List.of(
                                nullSafe(expiry.fullName()),
                                titleish(expiry.mainRole()),
                                nullSafe(expiry.kind()) + " — " + nullSafe(expiry.subject()),
                                nullSafe(expiry.expiresOn()),
                                String.valueOf(daysTo(expiry.expiresOn())),
                                nullSafe(expiry.status())))
                        .toList();
            }

            @Override
            public String note() {
                return "The window above is read as the horizon: a ninety-day window lists what "
                        + "expires within ninety days. Anything already expired is listed first "
                        + "with a negative count, because it is the only urgent line.";
            }
        };
    }

    /* ── MAINTENANCE — the defect record ──────────────────────────────── */

    @Bean
    ReportRunner defectReport(TechLogService techLogService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "MX-DEFECTS";
            }

            @Override
            public String module() {
                return "Maintenance";
            }

            @Override
            public String subtitle() {
                return "Defects raised, deferred and rectified";
            }

            @Override
            public List<String> columns() {
                return List.of("Aircraft", "Type", "ATA", "System", "Defect", "Reported",
                        "Reported by", "Status", "MEL reference", "Rectification");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return techLogService.findBoard(tenantId).defects().stream()
                        .filter(defect -> within(defect.reportedAt(), from, to))
                        .map(defect -> List.of(
                                nullSafe(defect.registration()),
                                nullSafe(defect.icaoType()),
                                nullSafe(defect.ataChapter()),
                                nullSafe(defect.system()),
                                nullSafe(defect.description()),
                                date(defect.reportedAt()),
                                nullSafe(defect.reportedByName()),
                                nullSafe(defect.status()),
                                nullSafe(defect.melReference()),
                                nullSafe(defect.correctiveAction())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var board = techLogService.findBoard(tenantId);
                return List.of(
                        kpi("Open", String.valueOf(board.open()),
                                board.openAircraft() + " aircraft",
                                board.open() == 0 ? "good" : "bad"),
                        kpi("Deferred under MEL", String.valueOf(board.deferred()),
                                board.deferredRegistrations().isEmpty()
                                        ? "none" : String.join(", ", board.deferredRegistrations()),
                                board.deferred() == 0 ? "good" : "warn"),
                        kpi("Repeat defects", String.valueOf(board.repeatDefects()),
                                "same ATA within 30 days",
                                board.repeatDefects() == 0 ? "good" : "bad"),
                        kpi("Average time to close",
                                board.averageDaysToClose() == null
                                        ? "—" : board.averageDaysToClose() + " days",
                                "raised to rectified", "neutral"));
            }

            @Override
            public String note() {
                return "Filtered on the date the defect was raised, not the date it was closed: a "
                        + "defect raised inside the window and still open is the one that matters, "
                        + "and filtering on closure would hide it.";
            }
        };
    }

    /* ── MAINTENANCE — airworthiness ──────────────────────────────────── */

    @Bean
    ReportRunner airworthinessReport(CamoService camoService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "MX-ARC";
            }

            @Override
            public String module() {
                return "Maintenance";
            }

            @Override
            public String subtitle() {
                return "Certificates, directives and life-limited parts";
            }

            @Override
            public List<String> columns() {
                return List.of("Registration", "Type", "Status", "ARC certificate", "ARC expiry",
                        "Days to ARC", "Open AD/SB", "Overdue tasks", "Critical LLPs",
                        "Open work orders");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return camoService.findFleetStatus(tenantId).stream()
                        .sorted(Comparator.comparing(row -> row.arcDaysLeft() == null
                                ? Integer.MAX_VALUE : row.arcDaysLeft()))
                        .map(row -> List.of(
                                row.registration(),
                                nullSafe(row.icaoType()),
                                nullSafe(row.status()),
                                nullSafe(row.arcCertificateNo()),
                                nullSafe(row.arcExpiresOn()),
                                nullSafe(row.arcDaysLeft()),
                                String.valueOf(row.openDirectives()),
                                String.valueOf(row.overdueTasks()),
                                String.valueOf(row.criticalLlps()),
                                String.valueOf(row.openWorkOrders())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var fleet = camoService.findFleetStatus(tenantId);
                long expired = fleet.stream()
                        .filter(row -> row.arcDaysLeft() != null && row.arcDaysLeft() < 0).count();
                long overdue = fleet.stream().mapToInt(row -> row.overdueTasks()).sum();
                return List.of(
                        kpi("Fleet", String.valueOf(fleet.size()), "registrations managed",
                                "neutral"),
                        kpi("ARC expired", String.valueOf(expired),
                                expired == 0 ? "none" : "not airworthy",
                                expired == 0 ? "good" : "bad"),
                        kpi("Overdue tasks", String.valueOf(overdue), "across the fleet",
                                overdue == 0 ? "good" : "bad"),
                        kpi("Critical LLPs",
                                String.valueOf(fleet.stream().mapToInt(row -> row.criticalLlps()).sum()),
                                "under 10% life remaining", "warn"));
            }

            @Override
            public String note() {
                return "The period above is the expiry horizon, not a filter on events: the report "
                        + "answers what falls due, which is a question about the future rather than "
                        + "about a past window.";
            }
        };
    }

    /* ── SALES — the aircraft register ────────────────────────────────── */

    @Bean
    ReportRunner aircraftRegisterReport(CamoService camoService, LegService legService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "FLEET-REG";
            }

            @Override
            public String module() {
                return "Sales";
            }

            @Override
            public String subtitle() {
                return "The fleet as an owner reads it";
            }

            @Override
            public List<String> columns() {
                return List.of("Registration", "Type", "Model", "Status",
                        "Hours since new", "Cycles since new", "Sectors in period",
                        "Block in period");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                Map<String, List<LegDto>> byTail = legService.findProgrammeRange(tenantId, from, to)
                        .stream()
                        .filter(leg -> leg.registration() != null)
                        .collect(Collectors.groupingBy(LegDto::registration));

                return camoService.findFleetStatus(tenantId).stream()
                        .map(row -> {
                            List<LegDto> flown = byTail.getOrDefault(row.registration(), List.of());
                            return List.of(
                                    row.registration(),
                                    nullSafe(row.icaoType()),
                                    nullSafe(row.model()),
                                    nullSafe(row.status()),
                                    nullSafe(row.hoursSinceNew()),
                                    nullSafe(row.cyclesSinceNew()),
                                    String.valueOf(flown.size()),
                                    hhmm(flown.stream()
                                            .mapToLong(CatalogueReportRunners::blockMinutes).sum()));
                        })
                        .toList();
            }

            @Override
            public String note() {
                return "A registration that flew nothing in the period shows zero rather than "
                        + "dropping off the list: an owner asking about their aircraft is entitled "
                        + "to see that it did not fly.";
            }
        };
    }

    /* ── SAFETY — occurrences and experience feedback ─────────────────── */

    @Bean
    ReportRunner safetyReportsReport(SafetyOverviewService overviewService,
                                     SmsRegisterService registerService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "SAF-REX";
            }

            @Override
            public String module() {
                return "Safety";
            }

            @Override
            public String subtitle() {
                return "Occurrences and experience feedback together";
            }

            @Override
            public List<String> columns() {
                return List.of("Kind", "Reference", "Title", "Category", "Date",
                        "Risk", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                List<List<String>> rows = new ArrayList<>();

                overviewService.findOverview(tenantId).recentOccurrences().stream()
                        .filter(occurrence -> within(occurrence.occurredAt(), from, to))
                        .forEach(occurrence -> rows.add(List.of(
                                "Occurrence",
                                nullSafe(occurrence.reference()),
                                nullSafe(occurrence.title()),
                                nullSafe(occurrence.category()),
                                date(occurrence.occurredAt()),
                                nullSafe(occurrence.riskLevel()),
                                nullSafe(occurrence.status()))));

                registerService.findRexLibrary(tenantId, null).stream()
                        .filter(rex -> rex.publishedOn() != null
                                && !rex.publishedOn().isBefore(from)
                                && !rex.publishedOn().isAfter(to))
                        .forEach(rex -> rows.add(List.of(
                                "REX",
                                rex.reference(),
                                rex.title(),
                                rex.category(),
                                rex.publishedOn().toString(),
                                "—",
                                rex.status())));

                return rows;
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var overview = overviewService.findOverview(tenantId);
                var rex = registerService.findRexLibrary(tenantId, null);
                return List.of(
                        kpi("Occurrences this month",
                                String.valueOf(overview.occurrencesThisMonth()),
                                "filed into the register", "neutral"),
                        kpi("Still open", String.valueOf(overview.occurrencesStillOpen()),
                                "not yet closed",
                                overview.occurrencesStillOpen() == 0 ? "good" : "warn"),
                        kpi("Above tolerance", String.valueOf(overview.risksAboveTolerance()),
                                "risk index demands action",
                                overview.risksAboveTolerance() == 0 ? "good" : "bad"),
                        kpi("REX published",
                                String.valueOf(rex.stream()
                                        .filter(entry -> "published".equals(entry.status())).count()),
                                "lessons shared company-wide", "good"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                var byDomain = overviewService.findOverview(tenantId).riskByDomain();
                return List.of(chart("rp-saf-1", "Risk by domain", "bar", "half",
                        byDomain.stream().map(entry -> entry.label()).toList(),
                        List.of(series("Residual risk index",
                                byDomain.stream().map(entry -> (double) entry.residualIndex()).toList(),
                                RED))));
            }

            @Override
            public String note() {
                return "Two different things on one page, and the Kind column keeps them apart. An "
                        + "occurrence is something that happened; a REX is a lesson somebody chose "
                        + "to pass on, and it is never disciplinary.";
            }
        };
    }

    /* ── helpers ──────────────────────────────────────────────────────── */

    private static List<PersonDto> people(CrewPeopleService service, UUID tenantId) {
        return service.findAll(tenantId, null, null, true);
    }

    private static long blockMinutes(LegDto leg) {
        if (leg.outAt() != null && leg.inAt() != null) {
            return Duration.between(leg.outAt(), leg.inAt()).toMinutes();
        }
        if (leg.std() != null && leg.sta() != null) {
            return Duration.between(leg.std(), leg.sta()).toMinutes();
        }
        return 0;
    }

    /** Days to a date, negative once it has passed. Derived, never stored. */
    private static long daysTo(LocalDate date) {
        return date == null ? 0
                : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), date);
    }

    private static boolean within(java.time.OffsetDateTime at, LocalDate from, LocalDate to) {
        if (at == null) {
            return false;
        }
        LocalDate day = at.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        return !day.isBefore(from) && !day.isAfter(to);
    }

    private static String date(java.time.OffsetDateTime at) {
        return at == null ? "—"
                : at.atZoneSameInstant(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static String hhmm(long minutes) {
        return String.format("%d:%02d", minutes / 60, Math.abs(minutes % 60));
    }

    /** "FIRST_OFFICER" reads badly in a report an owner sees. */
    private static String titleish(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String spaced = value.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String nullSafe(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static ReportKpiDto kpi(String label, String value, String sub, String tone) {
        return new ReportKpiDto(label, value, sub, tone, null);
    }

    private static ReportChartDto chart(String id, String title, String kind, String width,
                                        List<String> labels, List<ReportSeriesDto> series) {
        return new ReportChartDto(id, title, kind, width, labels, series);
    }

    private static ReportSeriesDto series(String label, List<Double> data, String colour) {
        return new ReportSeriesDto(label, data, colour);
    }
}
