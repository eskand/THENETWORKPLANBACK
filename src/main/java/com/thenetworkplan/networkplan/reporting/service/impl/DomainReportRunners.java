package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.crew.service.CrewPeopleService;
import com.thenetworkplan.networkplan.mel.service.MelService;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import com.thenetworkplan.networkplan.safety.service.SafetyService;
import com.thenetworkplan.networkplan.sales.service.SalesService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The seven reports of the other domains.
 *
 * <p>Each one asks the owning module's service; none reads another schema.
 * That is the whole point of the runner: the reporting module knows how to
 * lay out a table, and nothing about anybody's tables.
 */
@Configuration
public class DomainReportRunners {

    @Bean
    ReportRunner crewBlockTimeReport(CrewPeopleService crewPeopleService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-FTL";
            }

            @Override
            public List<String> columns() {
                return List.of("Staff", "Name", "Role", "Block 7 d", "Block 28 d", "Block 365 d", "Documents");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return crewPeopleService.findAll(tenantId, null, null, true).stream()
                        .map(person -> List.of(
                                person.staffNo(),
                                person.fullName(),
                                person.mainRole(),
                                hhmm(person.blockMinutes7d()),
                                hhmm(person.blockMinutes28d()),
                                hhmm(person.blockMinutes365d()),
                                person.documentStatus()))
                        .toList();
            }

            @Override
            public String note() {
                return "Rolling windows ending today, summed from crew.duty_periods. These are "
                        + "totals, not a legality verdict: the comparison with ORO.FTL.210 belongs "
                        + "to the FTL engine of sprint S7.";
            }
        };
    }

    @Bean
    ReportRunner crewExpiriesReport(CrewPeopleService crewPeopleService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "CREW-EXP";
            }

            @Override
            public List<String> columns() {
                return List.of("Staff", "Name", "Role", "What expires", "Subject",
                        "Expires on", "Days left", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                int horizon = (int) Math.max(1, ChronoUnit.DAYS.between(LocalDate.now(), to));
                return crewPeopleService.findExpiring(tenantId, horizon).stream()
                        .map(expiry -> List.of(
                                expiry.staffNo(),
                                expiry.fullName(),
                                expiry.mainRole(),
                                expiry.kind(),
                                nullSafe(expiry.subject()),
                                nullSafe(expiry.expiresOn()),
                                nullSafe(expiry.daysRemaining()),
                                expiry.status()))
                        .toList();
            }

            @Override
            public String note() {
                return "Licences, medicals, recurrent training and qualifications lapsing before "
                        + "the end of the window, worst first. A document with no date on file is "
                        + "included and shown as UNKNOWN — it is not treated as valid.";
            }
        };
    }

    @Bean
    ReportRunner maintenanceDueReport(CamoService camoService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "MX-DUE";
            }

            @Override
            public List<String> columns() {
                return List.of("Tail", "Task", "Title", "Due on", "Days left",
                        "Hours left", "Cycles left", "Driven by", "Status");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                int horizon = (int) Math.max(1, ChronoUnit.DAYS.between(LocalDate.now(), to));
                return camoService.findDueList(tenantId, horizon).stream()
                        .map(item -> List.of(
                                item.registration(),
                                item.code(),
                                item.title(),
                                nullSafe(item.dueOn()),
                                nullSafe(item.remainingDays()),
                                nullSafe(item.remainingHours()),
                                nullSafe(item.remainingCycles()),
                                item.drivingLimit(),
                                item.status()))
                        .toList();
            }

            @Override
            public String note() {
                return "Everything overdue shows whatever its date; the rest is limited to the "
                        + "window. A task with no limit on file is UNKNOWN, never in date.";
            }
        };
    }

    @Bean
    ReportRunner melReport(MelService melService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "MX-MEL";
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
                                item.dueAt() == null ? "per MEL remark" : item.dueAt().toLocalDate().toString(),
                                nullSafe(item.daysRemaining()),
                                item.blocksDispatch() ? "yes" : "no",
                                item.dueStatus()))
                        .toList();
            }

            @Override
            public String note() {
                return "Deferrals in force today. The rectification interval comes from the "
                        + "operator MEL line; a category A line without one shows "
                        + "\"per MEL remark\" rather than a date nobody computed.";
            }
        };
    }

    @Bean
    ReportRunner permitsReport(PermitService permitService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "TS-PERMITS";
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
                                nullSafe(permit.recipient()),
                                permit.sentAt() == null ? "not sent" : permit.sentAt().toLocalDate().toString(),
                                nullSafe(permit.reference())))
                        .toList();
            }

            @Override
            public String note() {
                return "Permit requests not confirmed, for legs departing inside the window. "
                        + "The window is on the flight, not on the day the request was raised.";
            }
        };
    }

    @Bean
    ReportRunner occurrencesReport(SafetyService safetyService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "SAF-OCC";
            }

            @Override
            public List<String> columns() {
                return List.of("Reference", "Occurred", "Category", "Title",
                        "Risk", "Status", "Filed with authority");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                int window = (int) Math.max(1, ChronoUnit.DAYS.between(from, to));
                return safetyService.findReporting(tenantId, window).occurrences().stream()
                        .map(occurrence -> List.of(
                                occurrence.reference(),
                                occurrence.occurredAt().toLocalDate().toString(),
                                occurrence.category(),
                                occurrence.title(),
                                occurrence.riskLevel() == null ? "not assessed" : occurrence.riskLevel(),
                                occurrence.status(),
                                occurrence.eccairsExportedAt() == null
                                        ? "not filed"
                                        : occurrence.eccairsReference()))
                        .toList();
            }

            @Override
            public String note() {
                return "Anonymous reports appear without their reporter, by design. "
                        + "\"Not assessed\" means no risk level has been recorded — it is not a "
                        + "low risk.";
            }
        };
    }

    @Bean
    ReportRunner salesPipelineReport(SalesService salesService) {
        return new ReportRunner() {
            @Override
            public String code() {
                return "COM-PIPE";
            }

            @Override
            public List<String> columns() {
                return List.of("Reference", "Client", "Route", "Departure",
                        "Feasibility", "Status", "Best quote", "Currency");
            }

            @Override
            public List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to) {
                return salesService.findBoard(tenantId, null).requests().stream()
                        .filter(request -> {
                            LocalDate day = request.departureAt().toLocalDate();
                            return !day.isBefore(from) && !day.isAfter(to);
                        })
                        .map(request -> List.of(
                                request.reference(),
                                request.clientName(),
                                request.depIcao() + "–" + request.arrIcao(),
                                request.departureAt().toLocalDate().toString(),
                                request.feasibility(),
                                request.status(),
                                nullSafe(request.bestQuoteTotal()),
                                nullSafe(request.bestQuoteCurrency())))
                        .toList();
            }

            @Override
            public String note() {
                return "Filtered on the departure date, not on the day the request arrived. "
                        + "Each total is in the currency of its own quote: they are not added "
                        + "together here.";
            }
        };
    }

    private static String hhmm(long minutes) {
        return String.format("%d:%02d", minutes / 60, minutes % 60);
    }

    private static String nullSafe(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }
}
