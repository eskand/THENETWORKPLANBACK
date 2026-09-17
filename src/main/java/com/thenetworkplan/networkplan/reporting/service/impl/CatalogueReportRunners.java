package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.camo.dto.FleetStatusRowDto;
import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportChartDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportKpiDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportSeriesDto;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.OccurrenceDto;
import com.thenetworkplan.networkplan.safety.service.SafetyService;
import com.thenetworkplan.networkplan.techlog.dto.TechLogBoardDto.DefectRowDto;
import com.thenetworkplan.networkplan.techlog.service.TechLogService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Maintenance, fleet and safety — the four reports those three modules answer.
 *
 * <p><b>Each runner asks the owning module's service.</b> A defect report that
 * went straight to {@code camo.defects} would be a second opinion about what
 * « deferred » means, and the two would drift apart the first time the tech log
 * changed a rule.
 *
 * <p><b>Two of these read forward, not back.</b> Airworthiness and the aircraft
 * register answer what falls due and what is serviceable — questions about the
 * future. The period above them is the horizon, not a filter on past events,
 * and each note says so rather than letting an operator assume otherwise.
 */
@Configuration
public class CatalogueReportRunners {

    /* ══════════════ TECH LOG — defects and deferrals ═════════════════════ */

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
                return "Open, deferred (MEL) and closed defects, by system and aircraft";
            }

            @Override
            public String scope() {
                return "Fleet + period on the date the defect was raised";
            }

            @Override
            public List<String> columns() {
                return List.of("Raised", "Tail", "Type", "ATA", "System", "Defect", "Status",
                        "MEL ref", "Reported by", "Flight", "Rectification");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return defects(techLogService, tenantId, from, to).stream()
                        .map(defect -> List.of(
                                date(defect.reportedAt()),
                                Rp.text(defect.registration()),
                                Rp.text(defect.icaoType()),
                                Rp.text(defect.ataChapter()),
                                Rp.text(defect.system()),
                                Rp.text(defect.description()),
                                Rp.text(defect.status()),
                                Rp.text(defect.melReference()),
                                Rp.text(defect.reportedByName()),
                                Rp.text(defect.flightRef()),
                                Rp.text(defect.correctiveAction())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var board = techLogService.findBoard(tenantId);
                List<DefectRowDto> rows = defects(techLogService, tenantId, from, to);
                long open = rows.stream().filter(defect -> is(defect, "OPEN")).count();
                long deferred = rows.stream().filter(defect -> is(defect, "DEFERRED")).count();
                long closed = rows.stream().filter(defect -> is(defect, "CLOSED")).count();
                long affected = rows.stream()
                        .filter(defect -> is(defect, "OPEN") || is(defect, "DEFERRED"))
                        .map(DefectRowDto::registration).distinct().count();
                List<Rp.Bucket> byAta = Rp.tally(rows, CatalogueReportRunners::ata);

                return List.of(
                        Rp.kpi("Open defects", String.valueOf(open),
                                rows.stream().filter(defect -> is(defect, "OPEN"))
                                        .map(DefectRowDto::registration).distinct().count()
                                        + " aircraft",
                                open == 0 ? "good" : "bad"),
                        Rp.kpi("Deferred (MEL)", String.valueOf(deferred),
                                deferred == 0 ? "none" : "dispatch under a deferral",
                                deferred == 0 ? "good" : "warn"),
                        Rp.kpi("Closed", String.valueOf(closed), "rectified and signed off",
                                "good"),
                        Rp.kpi("Aircraft affected", affected + " / " + board.fleetSize(),
                                "open or deferred items"),
                        Rp.kpi("Most affected system",
                                byAta.isEmpty() ? Rp.EMPTY : byAta.get(0).key(),
                                byAta.isEmpty() ? "no data" : byAta.get(0).count() + " entries",
                                "warn"),
                        Rp.kpi("Repeat defects", String.valueOf(board.repeatDefects()),
                                "same ATA within 30 days",
                                board.repeatDefects() == 0 ? "good" : "bad"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<DefectRowDto> rows = defects(techLogService, tenantId, from, to);
                List<Rp.Bucket> byTail = Rp.tally(rows, DefectRowDto::registration);

                return List.of(
                        Rp.donut("rpc1", "Defects by status", "third",
                                List.of(new Rp.Bucket("Open", rows.stream()
                                                .filter(defect -> is(defect, "OPEN")).count()),
                                        new Rp.Bucket("Deferred (MEL)", rows.stream()
                                                .filter(defect -> is(defect, "DEFERRED")).count()),
                                        new Rp.Bucket("Closed", rows.stream()
                                                .filter(defect -> is(defect, "CLOSED")).count())),
                                List.of(Rp.RED, Rp.AMBER, Rp.GREEN)),
                        Rp.hbar("rpc2", "Defects by ATA chapter", "twothirds",
                                Rp.topN(Rp.tally(rows, CatalogueReportRunners::ata), 12), Rp.NAVY2),
                        Rp.bar("rpc3", "Defects by aircraft", "half", byTail, Rp.BLUE),
                        Rp.stacked("rpc4", "Open vs deferred by aircraft", "half",
                                byTail.stream().map(Rp.Bucket::key).toList(),
                                List.of(
                                        Rp.series("Open", byTail.stream()
                                                .map(bucket -> count(rows, bucket.key(), "OPEN"))
                                                .toList(), Rp.RED),
                                        Rp.series("Deferred", byTail.stream()
                                                .map(bucket -> count(rows, bucket.key(), "DEFERRED"))
                                                .toList(), Rp.AMBER),
                                        Rp.series("Closed", byTail.stream()
                                                .map(bucket -> count(rows, bucket.key(), "CLOSED"))
                                                .toList(), Rp.GREEN))));
            }

            @Override
            public String note() {
                return "Filtered on the date the defect was raised, not the date it was closed: a "
                        + "defect raised inside the window and still open is the one that matters, "
                        + "and filtering on closure would hide exactly those. Deferred items carry "
                        + "an MEL reference and a rectification interval — cross-check them against "
                        + "the MEL / CDL module before dispatch. A defect with no ATA chapter on "
                        + "file is listed under \"—\" rather than being assigned to the nearest "
                        + "system.";
            }
        };
    }

    /* ══════════════ CAMO — airworthiness status ══════════════════════════ */

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
                return "ARC validity, AD/SB status, life-limited parts and next checks";
            }

            @Override
            public String scope() {
                return "Fleet applied; period is the expiry / due window";
            }

            @Override
            public List<String> columns() {
                return List.of("Tail", "Type", "ARC certificate", "ARC expiry", "Days left",
                        "Next check", "Due on", "Days to check", "AD/SB open", "Overdue tasks",
                        "Critical LLPs", "Lowest LLP", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return camoService.findFleetStatus(tenantId).stream()
                        .sorted(Comparator.comparing(row -> row.arcDaysLeft() == null
                                ? Long.MAX_VALUE : row.arcDaysLeft()))
                        .map(row -> List.of(
                                row.registration(),
                                Rp.text(row.icaoType()),
                                Rp.text(row.arcCertificateNo()),
                                Rp.iso(row.arcExpiresOn()),
                                row.arcDaysLeft() == null ? Rp.EMPTY
                                        : String.valueOf(row.arcDaysLeft()),
                                Rp.text(row.nextDueCode()),
                                Rp.iso(row.nextDueOn()),
                                row.nextDueInDays() == null ? Rp.EMPTY
                                        : String.valueOf(row.nextDueInDays()),
                                String.valueOf(row.openDirectives()),
                                String.valueOf(row.overdueTasks()),
                                String.valueOf(row.criticalLlps()),
                                row.worstLlpPercent() == null ? Rp.EMPTY
                                        : row.worstLlpPercent() + " %",
                                Rp.text(row.status())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<FleetStatusRowDto> fleet = camoService.findFleetStatus(tenantId);
                long arc90 = fleet.stream()
                        .filter(row -> row.arcDaysLeft() != null && row.arcDaysLeft() < 90).count();
                long expired = fleet.stream()
                        .filter(row -> row.arcDaysLeft() != null && row.arcDaysLeft() < 0).count();
                int open = fleet.stream().mapToInt(FleetStatusRowDto::openDirectives).sum();
                int overdue = fleet.stream().mapToInt(FleetStatusRowDto::overdueTasks).sum();
                long dueInPeriod = fleet.stream()
                        .filter(row -> row.arcExpiresOn() != null
                                && !row.arcExpiresOn().isBefore(from)
                                && !row.arcExpiresOn().isAfter(to)).count();
                FleetStatusRowDto worstLlp = fleet.stream()
                        .filter(row -> row.worstLlpPercent() != null)
                        .min(Comparator.comparingInt(FleetStatusRowDto::worstLlpPercent))
                        .orElse(null);
                long serviceable = fleet.stream()
                        .filter(row -> "SERVICEABLE".equalsIgnoreCase(row.status())
                                || "OK".equalsIgnoreCase(row.status())).count();

                return List.of(
                        Rp.kpi("ARC expiring < 90 d", String.valueOf(arc90),
                                expired == 0 ? "none expired" : expired + " already expired",
                                arc90 == 0 ? "good" : expired == 0 ? "warn" : "bad"),
                        Rp.kpi("AD/SB open", String.valueOf(open), "across the fleet",
                                Rp.countTone(open)),
                        Rp.kpi("Overdue tasks", String.valueOf(overdue),
                                overdue == 0 ? "none" : "immediate action",
                                overdue == 0 ? "good" : "bad"),
                        Rp.kpi("Airworthy", serviceable + " / " + fleet.size(),
                                Rp.pct(serviceable, fleet.size()) + " % of the fleet",
                                serviceable == fleet.size() ? "good" : "warn",
                                Rp.pct(serviceable, fleet.size())),
                        Rp.kpi("ARC due in period", String.valueOf(dueInPeriod), from + " → " + to,
                                Rp.countTone(dueInPeriod)),
                        Rp.kpi("Worst LLP margin",
                                worstLlp == null ? Rp.EMPTY : worstLlp.worstLlpPercent() + " %",
                                worstLlp == null ? "no data" : worstLlp.registration(),
                                worstLlp != null && worstLlp.worstLlpPercent() < 30
                                        ? "bad" : "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<FleetStatusRowDto> fleet = camoService.findFleetStatus(tenantId);
                List<String> tails = fleet.stream().map(FleetStatusRowDto::registration).toList();

                return List.of(
                        new ReportChartDto("rpc1", "ARC days remaining", "bar", "half", tails,
                                List.of(new ReportSeriesDto("Days",
                                        fleet.stream().map(row -> row.arcDaysLeft() == null
                                                ? 0d : (double) row.arcDaysLeft()).toList(), null,
                                        fleet.stream().map(row -> {
                                            long left = row.arcDaysLeft() == null
                                                    ? 0 : row.arcDaysLeft();
                                            return left < 90 ? Rp.RED
                                                    : left < 180 ? Rp.AMBER : Rp.GREEN;
                                        }).toList()))),
                        new ReportChartDto("rpc2", "Days to next scheduled check", "bar", "half",
                                tails, List.of(Rp.series("Days",
                                        fleet.stream().map(row -> row.nextDueInDays() == null
                                                ? 0d : (double) row.nextDueInDays()).toList(),
                                        Rp.NAVY2))),
                        Rp.donut("rpc3", "Fleet airworthiness status", "third",
                                Rp.tally(fleet, FleetStatusRowDto::status)),
                        Rp.stacked("rpc4", "Tasks open vs overdue", "twothirds", tails,
                                List.of(
                                        Rp.series("Open", fleet.stream()
                                                .map(row -> (double) row.openTasks()).toList(),
                                                Rp.AMBER),
                                        Rp.series("Overdue", fleet.stream()
                                                .map(row -> (double) row.overdueTasks()).toList(),
                                                Rp.RED))));
            }

            @Override
            public String note() {
                return "The period above is the expiry horizon, not a filter on events: the report "
                        + "answers what falls due, which is a question about the future. ARC "
                        + "validity is tracked per M.A.901 — an aircraft whose ARC has lapsed "
                        + "cannot be released to service whatever its technical condition. LLP "
                        + "percentages are life remaining, so the low figure is the urgent one.";
            }
        };
    }

    /* ══════════════ AIRCRAFT AVAILABILITY — the fleet for a sales desk ═══ */

    @Bean
    ReportRunner aircraftAvailabilityReport(CamoService camoService, LegService legService) {
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
                return "Tail-by-tail status, type mix and technical availability for sales";
            }

            @Override
            public String scope() {
                return "Fleet applied; period drives the sectors-flown column";
            }

            @Override
            public List<String> columns() {
                return List.of("Registration", "Type", "Model", "Status", "ARC expiry",
                        "ARC days", "Next check", "Hours since new", "Sectors in period",
                        "Block in period", "Idle days");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                Map<String, List<LegDto>> byTail = flownByTail(legService, tenantId, from, to);
                int dayCount = Rp.days(from, to).size();

                return camoService.findFleetStatus(tenantId).stream()
                        .map(row -> {
                            List<LegDto> flown = byTail.getOrDefault(row.registration(), List.of());
                            long activeDays = flown.stream()
                                    .map(leg -> leg.std().toLocalDate()).distinct().count();
                            return List.of(
                                    row.registration(),
                                    Rp.text(row.icaoType()),
                                    Rp.text(row.model()),
                                    Rp.text(row.status()),
                                    Rp.iso(row.arcExpiresOn()),
                                    row.arcDaysLeft() == null ? Rp.EMPTY
                                            : String.valueOf(row.arcDaysLeft()),
                                    Rp.text(row.nextDueCode()),
                                    Rp.text(row.hoursSinceNew()),
                                    String.valueOf(flown.size()),
                                    Rp.dur(flown.stream()
                                            .mapToDouble(OpsReportRunners::blockHours).sum()),
                                    String.valueOf(Math.max(0, dayCount - activeDays)));
                        })
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<FleetStatusRowDto> fleet = camoService.findFleetStatus(tenantId);
                Map<String, List<LegDto>> byTail = flownByTail(legService, tenantId, from, to);
                long available = fleet.stream()
                        .filter(row -> "SERVICEABLE".equalsIgnoreCase(row.status())
                                || "OK".equalsIgnoreCase(row.status())).count();
                long down = fleet.size() - available;
                List<Rp.Bucket> byType = Rp.tally(fleet, FleetStatusRowDto::icaoType);
                int sectors = byTail.values().stream().mapToInt(List::size).sum();
                FleetStatusRowDto idlest = fleet.stream()
                        .min(Comparator.comparingInt(row ->
                                byTail.getOrDefault(row.registration(), List.of()).size()))
                        .orElse(null);

                return List.of(
                        Rp.kpi("Fleet size", String.valueOf(fleet.size()),
                                byType.size() + " distinct types"),
                        Rp.kpi("Available", String.valueOf(available),
                                Rp.pct(available, fleet.size()) + " % technical availability",
                                Rp.rateTone(Rp.pct(available, fleet.size()), 90, 75),
                                Rp.pct(available, fleet.size())),
                        Rp.kpi("Down (AOG / MX)", String.valueOf(down),
                                down == 0 ? "none" : "not offerable",
                                down == 0 ? "good" : "bad"),
                        Rp.kpi("Largest type", byType.isEmpty() ? Rp.EMPTY : byType.get(0).key(),
                                byType.isEmpty() ? "" : byType.get(0).count() + " aircraft",
                                "warn"),
                        Rp.kpi("Sectors in period", String.valueOf(sectors), from + " → " + to,
                                "warn"),
                        Rp.kpi("Offer first",
                                idlest == null ? Rp.EMPTY : idlest.registration(),
                                idlest == null ? ""
                                        : byTail.getOrDefault(idlest.registration(), List.of())
                                                .size() + " sectors flown"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<FleetStatusRowDto> fleet = camoService.findFleetStatus(tenantId);
                Map<String, List<LegDto>> byTail = flownByTail(legService, tenantId, from, to);

                return List.of(
                        Rp.donut("rpc1", "Status mix", "third",
                                Rp.tally(fleet, FleetStatusRowDto::status)),
                        Rp.bar("rpc2", "Aircraft per type", "third",
                                Rp.tally(fleet, FleetStatusRowDto::icaoType), Rp.NAVY2),
                        Rp.hbar("rpc3", "Sectors flown per tail", "third",
                                fleet.stream()
                                        .map(row -> new Rp.Bucket(row.registration(),
                                                byTail.getOrDefault(row.registration(),
                                                        List.of()).size()))
                                        .sorted(Comparator.comparingDouble(Rp.Bucket::value)
                                                .reversed())
                                        .toList(), Rp.BLUE));
            }

            @Override
            public String note() {
                return "Technical availability counts tails with no open grounding event. A "
                        + "registration that flew nothing in the period shows zero rather than "
                        + "dropping off the list: an owner asking about their aircraft is entitled "
                        + "to see that it did not fly, and a sales desk needs the idle ones first. "
                        + "Idle days are period days on which that tail flew no sector.";
            }
        };
    }

    /* ══════════════ SAFETY (SMS) — reports and occurrences ═══════════════ */

    @Bean
    ReportRunner safetyOccurrencesReport(SafetyService safetyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "SAF-OCC";
            }

            @Override
            public String module() {
                return "Safety (SMS)";
            }

            @Override
            public String subtitle() {
                return "Severity, status and type breakdown of SMS reports";
            }

            @Override
            public String scope() {
                return "Fleet where the flight is identifiable; anonymous reports carry no reporter";
            }

            @Override
            public List<String> columns() {
                return List.of("Ref", "Occurred", "Title", "Category", "Phase", "Severity",
                        "Status", "Reporter", "Aircraft", "Station", "Open actions",
                        "Filed with authority");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return occurrences(safetyService, tenantId, from, to).stream()
                        .map(occurrence -> List.of(
                                occurrence.reference(),
                                date(occurrence.occurredAt()),
                                Rp.text(occurrence.title()),
                                Rp.text(occurrence.category()),
                                Rp.text(occurrence.phaseOfFlight()),
                                occurrence.riskLevel() == null
                                        ? "not assessed" : occurrence.riskLevel(),
                                Rp.text(occurrence.status()),
                                occurrence.anonymous() ? "anonymous"
                                        : Rp.text(occurrence.reportedByName()),
                                Rp.text(occurrence.registration()),
                                Rp.text(occurrence.stationIcao()),
                                String.valueOf(occurrence.openActions()),
                                occurrence.eccairsExportedAt() == null
                                        ? "not filed" : Rp.text(occurrence.eccairsReference())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                List<OccurrenceDto> rows = occurrences(safetyService, tenantId, from, to);
                long critical = rows.stream()
                        .filter(row -> "UNACCEPTABLE".equalsIgnoreCase(row.riskLevel())
                                || "HIGH".equalsIgnoreCase(row.riskLevel())).count();
                long open = rows.stream()
                        .filter(row -> !"CLOSED".equalsIgnoreCase(row.status())).count();
                long notAssessed = rows.stream().filter(row -> row.riskLevel() == null).count();
                List<Rp.Bucket> byCategory = Rp.tally(rows, OccurrenceDto::category);
                long filed = rows.stream()
                        .filter(row -> row.eccairsExportedAt() != null).count();

                return List.of(
                        Rp.kpi("Total reports", String.valueOf(rows.size()), "all severities"),
                        Rp.kpi("High or unacceptable", String.valueOf(critical),
                                critical == 0 ? "none" : "act before the next flight",
                                critical == 0 ? "good" : "bad"),
                        Rp.kpi("Open", String.valueOf(open),
                                Rp.pct(open, rows.size()) + " % not closed",
                                Rp.countTone(open)),
                        Rp.kpi("Closed", String.valueOf(rows.size() - open),
                                "investigation complete", "good"),
                        Rp.kpi("Not assessed", String.valueOf(notAssessed),
                                notAssessed == 0 ? "every report has a risk level"
                                        : "no risk level recorded",
                                notAssessed == 0 ? "good" : "warn"),
                        Rp.kpi("Filed with authority", String.valueOf(filed),
                                "ECCAIRS / Regulation (EU) 376/2014",
                                byCategory.isEmpty() ? "neutral" : "neutral"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                List<OccurrenceDto> rows = occurrences(safetyService, tenantId, from, to);
                List<Rp.Bucket> bySeverity = Rp.tally(rows,
                        row -> row.riskLevel() == null ? "Not assessed" : row.riskLevel());

                return List.of(
                        Rp.donut("rpc1", "By risk level", "third", bySeverity,
                                bySeverity.stream()
                                        .map(bucket -> riskColour(bucket.key())).toList()),
                        Rp.donut("rpc2", "By workflow status", "third",
                                Rp.tally(rows, OccurrenceDto::status)),
                        Rp.hbar("rpc3", "By category", "third",
                                Rp.topN(Rp.tally(rows, OccurrenceDto::category), 10), Rp.NAVY2),
                        Rp.bar("rpc4", "Reports by station", "full",
                                Rp.topN(Rp.tally(rows, OccurrenceDto::stationIcao), 12), Rp.BLUE));
            }

            @Override
            public String note() {
                return "Filtered on the date the occurrence happened, not the date it was filed: "
                        + "a report raised late still belongs to the period it describes. "
                        + "Anonymous reports appear without their reporter by design — the "
                        + "confidentiality is the reason the report exists. \"Not assessed\" means "
                        + "no risk level has been recorded; it is not a low risk, and a report "
                        + "sitting there is the one to look at first. Severity and status follow "
                        + "the SMS workflow of ICAO Annex 19.";
            }
        };
    }

    /* ══════════════════════════ shared reading ═══════════════════════════ */

    private static List<DefectRowDto> defects(TechLogService techLogService, UUID tenantId,
                                              LocalDate from, LocalDate to) {
        return techLogService.findBoard(tenantId).defects().stream()
                .filter(defect -> within(defect.reportedAt(), from, to))
                .sorted(Comparator.comparing(DefectRowDto::reportedAt).reversed())
                .toList();
    }

    private static List<OccurrenceDto> occurrences(SafetyService safetyService, UUID tenantId,
                                                   LocalDate from, LocalDate to) {
        int window = (int) Math.max(1,
                java.time.temporal.ChronoUnit.DAYS.between(from, LocalDate.now(ZoneOffset.UTC)) + 1);
        return safetyService.findReporting(tenantId, window).occurrences().stream()
                .filter(occurrence -> within(occurrence.occurredAt(), from, to))
                .sorted(Comparator.comparing(OccurrenceDto::occurredAt).reversed())
                .toList();
    }

    private static Map<String, List<LegDto>> flownByTail(LegService legService, UUID tenantId,
                                                         LocalDate from, LocalDate to) {
        Map<String, List<LegDto>> byTail = new HashMap<>();
        legService.findProgrammeRange(tenantId, from, to).stream()
                .filter(leg -> leg.registration() != null
                        && !"CANCELLED".equalsIgnoreCase(leg.status()))
                .forEach(leg -> byTail
                        .computeIfAbsent(leg.registration(), key -> new java.util.ArrayList<>())
                        .add(leg));
        return byTail;
    }

    /** « ATA 24 — Electrical power », the label the prototype groups by. */
    private static String ata(DefectRowDto defect) {
        if (defect.ataChapter() == null || defect.ataChapter().isBlank()) {
            return Rp.EMPTY;
        }
        return defect.system() == null || defect.system().isBlank()
                ? "ATA " + defect.ataChapter()
                : "ATA " + defect.ataChapter() + " — " + defect.system();
    }

    private static boolean is(DefectRowDto defect, String status) {
        return status.equalsIgnoreCase(defect.status());
    }

    private static double count(List<DefectRowDto> rows, String registration, String status) {
        return rows.stream()
                .filter(defect -> registration.equals(defect.registration()) && is(defect, status))
                .count();
    }

    private static String riskColour(String level) {
        return switch (level == null ? "" : level.toUpperCase()) {
            case "UNACCEPTABLE", "CRITICAL" -> Rp.RED;
            case "HIGH" -> "#f97316";
            case "TOLERABLE", "MEDIUM" -> Rp.AMBER;
            case "ACCEPTABLE", "LOW" -> Rp.BLUE;
            case "NOT ASSESSED" -> Rp.GREY;
            default -> Rp.GREEN;
        };
    }

    private static boolean within(OffsetDateTime at, LocalDate from, LocalDate to) {
        if (at == null) {
            return false;
        }
        LocalDate day = at.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        return !day.isBefore(from) && !day.isAfter(to);
    }

    private static String date(OffsetDateTime at) {
        return at == null ? Rp.EMPTY
                : at.atZoneSameInstant(ZoneOffset.UTC).toLocalDate().toString();
    }
}
