package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.mel.service.MelService;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportChartDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportKpiDto;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import com.thenetworkplan.networkplan.sales.service.SalesService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The reports this product answers that the approved prototype does not ask.
 *
 * <p><b>Why they are kept, and kept apart.</b> The maintenance due list, the
 * MEL items in force and the outstanding permits are questions the application
 * already answers and somebody already reads; removing them to match the
 * annexe's catalogue exactly would take a working answer away. They sit in
 * their own configuration so that what is inside the approved perimeter and
 * what is beyond it can be told apart at a glance.
 *
 * <p>The commercial pipeline is in the annexe, and is here because it belongs
 * to the sales module like the other three belong to theirs.
 */
@Configuration
public class DomainReportRunners {

    /* ── the maintenance due list ─────────────────────────────────────────── */

    @Bean
    ReportRunner maintenanceDueReport(CamoService camoService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "MX-DUE";
            }

            @Override
            public String module() {
                return "Maintenance";
            }

            @Override
            public String subtitle() {
                return "Tasks falling due inside the window, and everything already overdue";
            }

            @Override
            public String scope() {
                return "Fleet applied; period is the due window";
            }

            @Override
            public List<String> columns() {
                return List.of("Tail", "Task", "Title", "Due on", "Days left",
                        "Hours left", "Cycles left", "Driven by", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return camoService.findDueList(tenantId, horizon(to)).stream()
                        .map(item -> List.of(
                                item.registration(),
                                item.code(),
                                item.title(),
                                Rp.text(item.dueOn()),
                                Rp.text(item.remainingDays()),
                                Rp.text(item.remainingHours()),
                                Rp.text(item.remainingCycles()),
                                item.drivingLimit(),
                                item.status()))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var due = camoService.findDueList(tenantId, horizon(to));
                long overdue = due.stream()
                        .filter(item -> "OVERDUE".equalsIgnoreCase(item.status())).count();
                long unknown = due.stream()
                        .filter(item -> "UNKNOWN".equalsIgnoreCase(item.status())).count();
                return List.of(
                        Rp.kpi("Tasks due", String.valueOf(due.size()),
                                "inside the window, plus everything overdue"),
                        Rp.kpi("Overdue", String.valueOf(overdue),
                                overdue == 0 ? "none" : "cannot be released",
                                overdue == 0 ? "good" : "bad"),
                        Rp.kpi("Aircraft affected",
                                String.valueOf(due.stream().map(item -> item.registration())
                                        .distinct().count()), "with at least one task due"),
                        Rp.kpi("No limit on file", String.valueOf(unknown),
                                unknown == 0 ? "every task has a limit" : "counted as unknown",
                                Rp.countTone(unknown)));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                var due = camoService.findDueList(tenantId, horizon(to));
                return List.of(
                        Rp.bar("rpc1", "Tasks due by aircraft", "half",
                                Rp.tally(due, item -> item.registration()), Rp.NAVY2),
                        Rp.donut("rpc2", "By driving limit", "half",
                                Rp.tally(due, item -> item.drivingLimit())));
            }

            @Override
            public String note() {
                return "Everything overdue shows whatever its date; the rest is limited to the "
                        + "window. A task with no limit on file is UNKNOWN, never in date.";
            }
        };
    }

    /* ── the MEL items in force ───────────────────────────────────────────── */

    @Bean
    ReportRunner melReport(MelService melService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "MX-MEL";
            }

            @Override
            public String module() {
                return "Maintenance";
            }

            @Override
            public String subtitle() {
                return "Deferrals in force today, with their rectification interval";
            }

            @Override
            public String scope() {
                return "Fleet applied; period is the due window";
            }

            @Override
            public List<String> columns() {
                return List.of("Tail", "Item", "Category", "Title", "Raised",
                        "Rectify by", "Days left", "Blocks dispatch", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return melService.findOpen(tenantId).stream()
                        .map(item -> List.of(
                                item.registration(),
                                item.reference(),
                                item.melCategory(),
                                item.title(),
                                item.raisedAt().toLocalDate().toString(),
                                item.dueAt() == null
                                        ? "per MEL remark" : item.dueAt().toLocalDate().toString(),
                                Rp.text(item.daysRemaining()),
                                item.blocksDispatch() ? "yes" : "no",
                                item.dueStatus()))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var open = melService.findOpen(tenantId);
                long blocking = open.stream().filter(item -> item.blocksDispatch()).count();
                return List.of(
                        Rp.kpi("Deferrals in force", String.valueOf(open.size()),
                                "open MEL and CDL items"),
                        Rp.kpi("Blocking dispatch", String.valueOf(blocking),
                                blocking == 0 ? "none" : "aircraft cannot be released",
                                blocking == 0 ? "good" : "bad"),
                        Rp.kpi("Aircraft affected",
                                String.valueOf(open.stream().map(item -> item.registration())
                                        .distinct().count()), "with an item in force"),
                        Rp.kpi("Expiring", String.valueOf(open.stream()
                                        .filter(item -> item.daysRemaining() != null
                                                && item.daysRemaining() <= 3).count()),
                                "three days or fewer to rectify", "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                var open = melService.findOpen(tenantId);
                return List.of(
                        Rp.donut("rpc1", "By MEL category", "half",
                                Rp.tally(open, item -> item.melCategory())),
                        Rp.bar("rpc2", "Items by aircraft", "half",
                                Rp.tally(open, item -> item.registration()), Rp.AMBER));
            }

            @Override
            public String note() {
                return "Deferrals in force today. The rectification interval comes from the "
                        + "operator MEL line; a category A line without one shows "
                        + "\"per MEL remark\" rather than a date nobody computed.";
            }
        };
    }

    /* ── permits outstanding ──────────────────────────────────────────────── */

    @Bean
    ReportRunner permitsReport(PermitService permitService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "TS-PERMITS";
            }

            @Override
            public String module() {
                return "Trip support";
            }

            @Override
            public String subtitle() {
                return "Permit requests not confirmed, for legs departing inside the window";
            }

            @Override
            public String scope() {
                return "Period applied to the flight, not to the request date";
            }

            @Override
            public List<String> columns() {
                return List.of("Country", "Kind", "Status", "Recipient", "Sent", "Reference");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return permitService.findOutstandingInWindow(tenantId, from, to).stream()
                        .map(permit -> List.of(
                                permit.countryIso2(),
                                permit.kind(),
                                permit.status(),
                                Rp.text(permit.recipient()),
                                permit.sentAt() == null
                                        ? "not sent" : permit.sentAt().toLocalDate().toString(),
                                Rp.text(permit.reference())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var permits = permitService.findOutstandingInWindow(tenantId, from, to);
                long unsent = permits.stream().filter(permit -> permit.sentAt() == null).count();
                return List.of(
                        Rp.kpi("Outstanding", String.valueOf(permits.size()),
                                "not confirmed", Rp.countTone(permits.size())),
                        Rp.kpi("Not yet sent", String.valueOf(unsent),
                                unsent == 0 ? "all requests issued" : "no request has gone out",
                                unsent == 0 ? "good" : "bad"),
                        Rp.kpi("Countries",
                                String.valueOf(permits.stream()
                                        .map(permit -> permit.countryIso2()).distinct().count()),
                                "awaiting an answer"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                var permits = permitService.findOutstandingInWindow(tenantId, from, to);
                return List.of(
                        Rp.bar("rpc1", "Outstanding by country", "half",
                                Rp.tally(permits, permit -> permit.countryIso2()), Rp.NAVY2),
                        Rp.donut("rpc2", "By kind", "half",
                                Rp.tally(permits, permit -> permit.kind())));
            }

            @Override
            public String note() {
                return "Permit requests not confirmed, for legs departing inside the window. "
                        + "The window is on the flight, not on the day the request was raised.";
            }
        };
    }

    /* ── the commercial pipeline ──────────────────────────────────────────── */

    @Bean
    ReportRunner salesPipelineReport(SalesService salesService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "COM-PIPE";
            }

            @Override
            public String module() {
                return "Sales";
            }

            @Override
            public String subtitle() {
                return "Clients, quotes and feasibility requests";
            }

            @Override
            public String scope() {
                return "Period + fleet on quotes; the client directory narrows only through them";
            }

            @Override
            public List<String> columns() {
                return List.of("Reference", "Client", "Route", "Departure",
                        "Feasibility", "Status", "Best quote", "Currency");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return requests(salesService, tenantId, from, to).stream()
                        .map(request -> List.of(
                                request.reference(),
                                request.clientName(),
                                request.depIcao() + "–" + request.arrIcao(),
                                request.departureAt().toLocalDate().toString(),
                                request.feasibility(),
                                request.status(),
                                Rp.text(request.bestQuoteTotal()),
                                Rp.text(request.bestQuoteCurrency())))
                        .toList();
            }

            @Override
            public List<ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
                var requests = requests(salesService, tenantId, from, to);
                var byStatus = Rp.tally(requests, request -> request.status());
                long quoted = requests.stream()
                        .filter(request -> request.bestQuoteTotal() != null).count();
                long clients = requests.stream()
                        .map(request -> request.clientName()).distinct().count();

                return List.of(
                        Rp.kpi("Requests", String.valueOf(requests.size()),
                                "departing in the window"),
                        Rp.kpi("Clients", String.valueOf(clients), "with an active request"),
                        Rp.kpi("Quoted", String.valueOf(quoted),
                                Rp.pct(quoted, requests.size()) + " % carry a price",
                                "neutral", Rp.pct(quoted, requests.size())),
                        Rp.kpi("Top status", byStatus.isEmpty() ? Rp.EMPTY : byStatus.get(0).key(),
                                byStatus.isEmpty() ? "" : byStatus.get(0).count() + " requests",
                                "warn"));
            }

            @Override
            public List<ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
                var requests = requests(salesService, tenantId, from, to);
                return List.of(
                        Rp.bar("rpc1", "Requests by status", "half",
                                Rp.tally(requests, request -> request.status()), Rp.NAVY2),
                        Rp.donut("rpc2", "Feasibility", "half",
                                Rp.tally(requests, request -> request.feasibility())));
            }

            @Override
            public String note() {
                return "Filtered on the departure date, not on the day the request arrived: a "
                        + "quote raised today for a flight next year belongs to next year. Each "
                        + "total is in the currency of its own quote and they are not added "
                        + "together here — a single figure across currencies would need a rate, "
                        + "and the rate would be invented.";
            }
        };
    }

    private static List<com.thenetworkplan.networkplan.sales.dto.SalesDtos.SalesRequestDto> requests(
            SalesService salesService, UUID tenantId, LocalDate from, LocalDate to) {
        return salesService.findBoard(tenantId, null).requests().stream()
                .filter(request -> {
                    LocalDate day = request.departureAt().toLocalDate();
                    return !day.isBefore(from) && !day.isAfter(to);
                })
                .toList();
    }

    /** The due window, counted from today: a task due yesterday is still due. */
    private static int horizon(LocalDate to) {
        return (int) Math.max(1, ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), to));
    }
}
