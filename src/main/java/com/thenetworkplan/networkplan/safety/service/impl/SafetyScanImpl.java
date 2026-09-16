package com.thenetworkplan.networkplan.safety.service.impl;

import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.camo.dto.FleetStatusRowDto;
import com.thenetworkplan.networkplan.camo.dto.LifeLimitedPartDto;
import com.thenetworkplan.networkplan.camo.service.CamoService;
import com.thenetworkplan.networkplan.crew.dto.CrewExpiryDto;
import com.thenetworkplan.networkplan.crew.service.CrewPeopleService;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.DomainCountDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.MonitoringFindingDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.MonitoringSummaryDto;
import com.thenetworkplan.networkplan.safety.service.SafetyScan;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The live safety scan.
 *
 * <p><b>What it is.</b> The operator's own data, read at the instant the button
 * is pressed, and turned into findings: crew documents about to lapse,
 * qualifications out of currency, airworthiness review certificates expired,
 * maintenance past a limit, deferred defects beyond their interval,
 * life-limited parts near the end of their life.
 *
 * <p><b>Why nothing is stored.</b> A stored finding goes on asserting a licence
 * expiry that was renewed yesterday. The prototype's monitor has the same
 * shape and the same instinct — it recomputes on demand — but it reads a
 * fabricated crew list; this one reads the crew.
 *
 * <p><b>It asks, it does not read.</b> Each module answers for its own rows:
 * CAMO for airworthiness, the crew module for documents and currency, the
 * airworthiness module for deferred defects. This class owns no query. That is
 * the same discipline the dispatch board and the CAMO screen follow, and it is
 * what stops six modules from each growing their own idea of "overdue".
 */
@Service
@Transactional(readOnly = true)
public class SafetyScanImpl implements SafetyScan {

    /** Under this many days to an expiry, the finding is critical rather than high. */
    private static final int CRITICAL_DAYS = 0;

    private static final int HIGH_DAYS = 14;

    private final CamoService camoService;
    private final CrewPeopleService crewPeopleService;
    private final AircraftService aircraftService;

    public SafetyScanImpl(CamoService camoService,
                          CrewPeopleService crewPeopleService,
                          AircraftService aircraftService) {
        this.camoService = camoService;
        this.crewPeopleService = crewPeopleService;
        this.aircraftService = aircraftService;
    }

    @Override
    public MonitoringSummaryDto scan(UUID tenantId) {
        List<MonitoringFindingDto> findings = new ArrayList<>();

        // The fleet is read once and its registrations reused: the deferred
        // defects come back keyed by aircraft id, and nobody reads an id.
        List<FleetStatusRowDto> fleet = camoService.findFleetStatus(tenantId);
        Map<UUID, String> registrations = fleet.stream()
                .collect(java.util.stream.Collectors.toMap(
                        FleetStatusRowDto::aircraftId, FleetStatusRowDto::registration));

        crewFindings(tenantId, findings);
        airworthinessFindings(fleet, tenantId, findings);
        deferredDefectFindings(tenantId, registrations, findings);

        return summarise(findings);
    }

    /**
     * Crew documents and qualifications.
     *
     * <p>Ninety days of horizon, which is the window the crew module itself
     * uses: long enough to book a recurrent course, short enough that the list
     * stays actionable. An expiry already past is critical — the person cannot
     * be rostered — and one inside a fortnight is high, because that is less
     * than the notice a course needs.
     */
    private void crewFindings(UUID tenantId, List<MonitoringFindingDto> findings) {
        for (CrewExpiryDto expiry : crewPeopleService.findExpiring(tenantId, 90)) {
            Long days = expiry.daysRemaining();
            String severity = days == null ? "medium"
                    : days < CRITICAL_DAYS ? "critical"
                            : days <= HIGH_DAYS ? "high" : "medium";
            // An unknown expiry is a finding in its own right, not a pass: a
            // document with no date on file cannot be shown to be current.
            String detail = days == null
                    ? expiry.kind() + " — no expiry date on file"
                    : days < 0
                            ? expiry.kind() + " expired " + Math.abs(days) + " days ago"
                            : expiry.kind() + " expires in " + days + " days";

            findings.add(new MonitoringFindingDto(
                    "CREW-" + expiry.kind() + "-" + expiry.personId(),
                    domainOf(expiry.kind()), severity, expiry.fullName(), detail));
        }
    }

    /**
     * Airworthiness: the review certificate, the due list and the parts.
     *
     * <p>An expired certificate is critical and nothing else on the tail
     * matters until it is renewed; a task past a limit is critical too, but for
     * one aircraft rather than one fleet. A life-limited part under fifteen per
     * cent is high: it grounds the aircraft on a date nobody scheduled.
     */
    private void airworthinessFindings(List<FleetStatusRowDto> fleet, UUID tenantId,
                                       List<MonitoringFindingDto> findings) {
        for (FleetStatusRowDto row : fleet) {
            if ("EXPIRED".equals(row.arcVerdict())) {
                findings.add(new MonitoringFindingDto(
                        "ARC-" + row.registration(), "CAMO", "critical", row.registration(),
                        "Airworthiness review certificate expired "
                                + Math.abs(row.arcDaysLeft()) + " days ago"));
            } else if ("NONE".equals(row.arcVerdict())) {
                findings.add(new MonitoringFindingDto(
                        "ARC-" + row.registration(), "CAMO", "critical", row.registration(),
                        "No airworthiness review certificate on file"));
            } else if ("CRITICAL".equals(row.arcVerdict())) {
                findings.add(new MonitoringFindingDto(
                        "ARC-" + row.registration(), "CAMO", "high", row.registration(),
                        "Airworthiness review certificate expires in " + row.arcDaysLeft() + " days"));
            }

            if (row.overdueTasks() > 0) {
                findings.add(new MonitoringFindingDto(
                        "MX-" + row.registration(), "CAMO", "critical", row.registration(),
                        row.overdueTasks() + " maintenance task"
                                + (row.overdueTasks() > 1 ? "s" : "") + " past a limit"));
            }

            if (!"SERVICEABLE".equals(row.status())) {
                findings.add(new MonitoringFindingDto(
                        "AC-" + row.registration(), "CAMO", "high", row.registration(),
                        row.statusReason() == null
                                ? "Aircraft is " + row.status().toLowerCase()
                                : row.statusReason()));
            }
        }

        for (LifeLimitedPartDto part : camoService.findLifeLimitedParts(tenantId)) {
            if (!"CRITICAL".equals(part.severity())) {
                // The list is sorted shortest life first, so the rest are further out.
                break;
            }
            findings.add(new MonitoringFindingDto(
                    "LLP-" + part.id(), "CAMO", "high", part.registration(),
                    part.name() + " at " + part.percentRemaining() + "% of life remaining"));
        }
    }

    /**
     * Deferred defects past their rectification interval, or blocking dispatch.
     *
     * <p>Past its interval is critical: the deferral has lapsed and the item is
     * now simply an open defect. Blocking dispatch is high — the aircraft still
     * cannot go, but the paperwork behind it is in order.
     *
     * @param registrations aircraft id to registration, from the fleet already read
     */
    private void deferredDefectFindings(UUID tenantId, Map<UUID, String> registrations,
                                        List<MonitoringFindingDto> findings) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (Map.Entry<UUID, List<MelItemDto>> entry
                : aircraftService.findOpenMelByAircraft(tenantId).entrySet()) {
            String registration = registrations.getOrDefault(entry.getKey(), "unknown tail");

            for (MelItemDto item : entry.getValue()) {
                boolean overdue = item.dueAt() != null && item.dueAt().isBefore(now);
                if (overdue) {
                    findings.add(new MonitoringFindingDto(
                            "MEL-" + item.id(), "TECHLOG", "critical", registration,
                            item.reference() + " — " + item.title() + " past its rectification interval"));
                } else if (item.blocksDispatch()) {
                    findings.add(new MonitoringFindingDto(
                            "MEL-" + item.id(), "TECHLOG", "high", registration,
                            item.reference() + " — " + item.title() + " blocks dispatch"));
                }
            }
        }
    }

    /** A licence and a type rating do not belong to the same part of the system. */
    private String domainOf(String kind) {
        if (kind == null) {
            return "CREW";
        }
        String upper = kind.toUpperCase();
        return upper.contains("LICENCE") || upper.contains("MEDICAL") || upper.contains("PASSPORT")
                ? "CREW"
                : "TRAINING";
    }

    private MonitoringSummaryDto summarise(List<MonitoringFindingDto> findings) {
        Map<String, int[]> byDomain = new LinkedHashMap<>();
        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;

        for (MonitoringFindingDto finding : findings) {
            int[] counts = byDomain.computeIfAbsent(finding.domain(), key -> new int[5]);
            counts[0]++;
            switch (finding.severity()) {
                case "critical" -> { critical++; counts[1]++; }
                case "high" -> { high++; counts[2]++; }
                case "medium" -> { medium++; counts[3]++; }
                default -> { low++; counts[4]++; }
            }
        }

        List<DomainCountDto> domains = byDomain.entrySet().stream()
                .map(entry -> new DomainCountDto(entry.getKey(), domainLabel(entry.getKey()),
                        entry.getValue()[0], entry.getValue()[1], entry.getValue()[2],
                        entry.getValue()[3], entry.getValue()[4]))
                .sorted((a, b) -> Integer.compare(b.total(), a.total()))
                .toList();

        return new MonitoringSummaryDto(
                OffsetDateTime.now(ZoneOffset.UTC),
                findings.size(), critical, high, medium, low,
                findings, domains);
    }

    private String domainLabel(String domain) {
        return switch (domain) {
            case "CAMO" -> "CAMO / Airworthiness";
            case "TECHLOG" -> "Tech Log / MEL";
            case "TRAINING" -> "Training";
            case "CREW" -> "Crew";
            case "OPS" -> "Flight Operations";
            case "AIRPORTS" -> "Aerodromes";
            default -> domain;
        };
    }
}
