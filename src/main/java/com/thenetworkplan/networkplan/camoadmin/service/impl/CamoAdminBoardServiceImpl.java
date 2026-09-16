package com.thenetworkplan.networkplan.camoadmin.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.AircraftStatus;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.camo.domain.AirworthinessReview;
import com.thenetworkplan.networkplan.camo.repository.AirworthinessReviewRepository;
import com.thenetworkplan.networkplan.camo.repository.WorkOrderRepository;
import com.thenetworkplan.networkplan.camoadmin.domain.AuditEvent;
import com.thenetworkplan.networkplan.camoadmin.domain.CamoDocument;
import com.thenetworkplan.networkplan.camoadmin.domain.Component;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.AlertDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.AuditEventDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.CamoAdminBoardDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.ComponentDto;
import com.thenetworkplan.networkplan.admin.repository.PlatformUserRepository;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.DocumentDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.UserDto;
import com.thenetworkplan.networkplan.camoadmin.repository.AuditEventRepository;
import com.thenetworkplan.networkplan.camoadmin.repository.CamoDocumentRepository;
import com.thenetworkplan.networkplan.camoadmin.repository.ComponentRepository;
import com.thenetworkplan.networkplan.camoadmin.repository.DirectiveApplicationRepository;
import com.thenetworkplan.networkplan.camoadmin.repository.ProgrammeTaskRepository;
import com.thenetworkplan.networkplan.camoadmin.service.CamoAdminBoardService;
import com.thenetworkplan.networkplan.mel.service.MelService;
import com.thenetworkplan.networkplan.techlog.domain.DefectStatus;
import com.thenetworkplan.networkplan.techlog.repository.DefectRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CAMO back office.
 *
 * <p><b>Alerts are found, not filed.</b> Each one is derived from the rows at
 * the moment of reading: an ARC that expired overnight appears this morning
 * without anybody having written an alert for it, and an alert whose cause has
 * been fixed disappears without anybody having closed it. A stored alert table
 * can promise neither.
 */
@Service
@Transactional(readOnly = true)
public class CamoAdminBoardServiceImpl implements CamoAdminBoardService {

    /** Thirty days: the window in which a CAMO can still act without disruption. */
    private static final int HORIZON_DAYS = 30;

    /** Membership of the department is what carries the authority, not seniority. */
    private static final List<String> INSIDE_CAMO = List.of(
            "CAMO_MANAGER", "AIRWORTHINESS", "ENGINEER", "PLANNER", "TECHNICAL_RECORDS");

    private final AircraftRepository aircraftRepository;
    private final AirworthinessReviewRepository reviewRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DirectiveApplicationRepository applicationRepository;
    private final ProgrammeTaskRepository programmeRepository;
    private final ComponentRepository componentRepository;
    private final CamoDocumentRepository documentRepository;
    private final AuditEventRepository auditRepository;
    private final DefectRepository defectRepository;
    private final MelService melService;
    private final PlatformUserRepository userRepository;

    public CamoAdminBoardServiceImpl(AircraftRepository aircraftRepository,
                                     AirworthinessReviewRepository reviewRepository,
                                     WorkOrderRepository workOrderRepository,
                                     DirectiveApplicationRepository applicationRepository,
                                     ProgrammeTaskRepository programmeRepository,
                                     ComponentRepository componentRepository,
                                     CamoDocumentRepository documentRepository,
                                     AuditEventRepository auditRepository,
                                     DefectRepository defectRepository,
                                     MelService melService,
                                     PlatformUserRepository userRepository) {
        this.aircraftRepository = aircraftRepository;
        this.reviewRepository = reviewRepository;
        this.workOrderRepository = workOrderRepository;
        this.applicationRepository = applicationRepository;
        this.programmeRepository = programmeRepository;
        this.componentRepository = componentRepository;
        this.documentRepository = documentRepository;
        this.auditRepository = auditRepository;
        this.defectRepository = defectRepository;
        this.melService = melService;
        this.userRepository = userRepository;
    }

    @Override
    public CamoAdminBoardDto findBoard(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Aircraft> fleet = aircraftRepository.findFleet(tenantId);
        List<AirworthinessReview> arcs = reviewRepository.findInForce(tenantId);
        List<ComponentDto> components = findComponents(tenantId, null);
        List<DocumentDto> documents = findDocuments(tenantId, null);

        int arcExpired = (int) arcs.stream()
                .filter(arc -> arc.getExpiresOn().isBefore(today))
                .count();
        int arcExpiring = (int) arcs.stream()
                .filter(arc -> !arc.getExpiresOn().isBefore(today))
                .filter(arc -> ChronoUnit.DAYS.between(today, arc.getExpiresOn()) <= HORIZON_DAYS)
                .count();

        var applications = applicationRepository.findAllForTenant(tenantId);
        int directivesOpen = (int) applications.stream()
                .filter(application -> "OPEN".equals(application.getStatus().name()))
                .count();
        int directivesOverdue = (int) applications.stream()
                .filter(application -> "OVERDUE".equals(application.getStatus().name()))
                .count();

        var defects = defectRepository.findDefects(tenantId, null, true);
        int defectsOpen = (int) defects.stream()
                .filter(defect -> defect.getStatus() == DefectStatus.OPEN)
                .count();

        int imported = (int) fleet.stream()
                .filter(aircraft -> aircraft.getSource() != null)
                .filter(aircraft -> !"manual".equals(aircraft.getSource().getType()))
                .count();

        return new CamoAdminBoardDto(
                fleet.size(),
                (int) fleet.stream().filter(a -> a.getStatus() == AircraftStatus.SERVICEABLE).count(),
                (int) fleet.stream().filter(a -> a.getStatus() == AircraftStatus.AOG).count(),
                arcs.size(), arcExpiring, arcExpired,
                directivesOpen, directivesOverdue,
                programmeRepository.findAll().size(),
                workOrderRepository.findOpen(tenantId).size(),
                defectsOpen,
                melService.findOpen(tenantId).size(),
                components.size(),
                (int) components.stream()
                        .filter(component -> "UNSERVICEABLE".equals(component.status()))
                        .count(),
                documents.size(),
                (int) documents.stream()
                        .filter(document -> "EXPIRED".equals(document.expiryStatus()))
                        .count(),
                (int) documents.stream()
                        .filter(document -> "EXPIRING".equals(document.expiryStatus()))
                        .count(),
                imported,
                alerts(fleet, arcs, documents, components, today),
                now);
    }

    /**
     * Everything wrong with the record right now.
     *
     * <p>Ordered by what stops an aircraft flying soonest: an expired ARC, then
     * an aircraft on the ground, then a document past its date, then a component
     * at its limit. A list ordered any other way makes a reader do the sorting.
     */
    private List<AlertDto> alerts(List<Aircraft> fleet, List<AirworthinessReview> arcs,
                                  List<DocumentDto> documents, List<ComponentDto> components,
                                  LocalDate today) {
        List<AlertDto> out = new ArrayList<>();

        arcs.stream()
                .filter(arc -> arc.getExpiresOn().isBefore(today))
                .forEach(arc -> out.add(new AlertDto("CRITICAL", "Airworthiness",
                        arc.getAircraft().getRegistration(),
                        "The airworthiness review certificate expired on " + arc.getExpiresOn()
                                + ". The aircraft is not airworthy.",
                        "aircraft", arc.getAircraft().getId())));

        fleet.stream()
                .filter(aircraft -> aircraft.getStatus() == AircraftStatus.AOG)
                .forEach(aircraft -> out.add(new AlertDto("CRITICAL", "Fleet",
                        aircraft.getRegistration(),
                        aircraft.getStatusReason() == null
                                ? "Declared AOG."
                                : "Declared AOG — " + aircraft.getStatusReason(),
                        "aircraft", aircraft.getId())));

        documents.stream()
                .filter(document -> "EXPIRED".equals(document.expiryStatus()))
                .forEach(document -> out.add(new AlertDto("CRITICAL", "Documents",
                        document.registration() == null ? document.title() : document.registration(),
                        document.category() + " " + nullSafe(document.reference())
                                + " expired on " + document.expiryDate() + ".",
                        "documents", document.id())));

        arcs.stream()
                .filter(arc -> !arc.getExpiresOn().isBefore(today))
                .filter(arc -> ChronoUnit.DAYS.between(today, arc.getExpiresOn()) <= HORIZON_DAYS)
                .forEach(arc -> out.add(new AlertDto("WARNING", "Airworthiness",
                        arc.getAircraft().getRegistration(),
                        "The ARC expires in " + ChronoUnit.DAYS.between(today, arc.getExpiresOn())
                                + " days, on " + arc.getExpiresOn() + ".",
                        "aircraft", arc.getAircraft().getId())));

        documents.stream()
                .filter(document -> "EXPIRING".equals(document.expiryStatus()))
                .forEach(document -> out.add(new AlertDto("WARNING", "Documents",
                        document.registration() == null ? document.title() : document.registration(),
                        document.category() + " expires in " + document.daysToExpiry() + " days.",
                        "documents", document.id())));

        components.stream()
                .filter(component -> component.lifeUsedPercent() != null)
                .filter(component -> component.lifeUsedPercent() >= 90)
                .forEach(component -> out.add(new AlertDto("WARNING", "Components",
                        nullSafe(component.registration()) + " · " + component.name(),
                        component.lifeUsedPercent() + "% of life used"
                                + (component.serialNumber() == null
                                        ? "." : " (S/N " + component.serialNumber() + ")."),
                        "components", component.id())));

        components.stream()
                .filter(component -> "UNSERVICEABLE".equals(component.status()))
                .forEach(component -> out.add(new AlertDto("WARNING", "Components",
                        component.name(),
                        "Marked unserviceable" + (component.serialNumber() == null
                                ? "." : " — S/N " + component.serialNumber() + "."),
                        "components", component.id())));

        // A fleet with no aircraft is not a quiet fleet; it is an empty record.
        if (fleet.isEmpty()) {
            out.add(new AlertDto("INFO", "Fleet", "No aircraft",
                    "The register holds no aircraft. Import the fleet before anything else.",
                    "import", null));
        }
        return out;
    }

    @Override
    public List<ComponentDto> findComponents(UUID tenantId, String category) {
        return componentRepository.findRegister(tenantId, blankToNull(category)).stream()
                .map(this::toDto)
                .toList();
    }

    private ComponentDto toDto(Component component) {
        BigDecimal hoursRemaining = remaining(component.getLifeLimitHours(), component.getTsn());
        Integer cyclesRemaining = component.getLifeLimitCycles() == null || component.getCsn() == null
                ? null
                : component.getLifeLimitCycles() - component.getCsn();

        return new ComponentDto(
                component.getId(),
                component.getCategory(),
                component.getAircraft() == null ? null : component.getAircraft().getId(),
                component.getAircraft() == null ? null : component.getAircraft().getRegistration(),
                component.getName(),
                component.getAtaChapter(),
                component.getPartNumber(),
                component.getSerialNumber(),
                component.getPosition(),
                component.getInstallDate(),
                component.getRemovalDate(),
                component.getTsn(), component.getCsn(),
                component.getTso(), component.getCso(),
                component.getLifeLimitHours(), component.getLifeLimitCycles(),
                component.getCalendarLimit(),
                hoursRemaining, cyclesRemaining,
                lifeUsedPercent(component),
                component.getOverhaulDue(),
                component.getNextInspection(),
                component.getEgtMargin(),
                component.getOilConsumption(),
                component.getStatus(),
                component.getNotes());
    }

    private BigDecimal remaining(BigDecimal limit, BigDecimal used) {
        return limit == null || used == null ? null : limit.subtract(used);
    }

    /**
     * How much life is gone, against the tightest of the limits that apply.
     *
     * <p>Hours, cycles and the calendar are three separate limits and a part
     * comes off at whichever arrives first. Reporting only the hours would let a
     * part sit on the aircraft past a calendar limit with a comfortable number
     * on the screen.
     */
    private Integer lifeUsedPercent(Component component) {
        Integer worst = null;

        if (component.getLifeLimitHours() != null && component.getTsn() != null
                && component.getLifeLimitHours().signum() > 0) {
            worst = component.getTsn()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(component.getLifeLimitHours(), 0, RoundingMode.HALF_UP)
                    .intValue();
        }
        if (component.getLifeLimitCycles() != null && component.getCsn() != null
                && component.getLifeLimitCycles() > 0) {
            int byCycles = component.getCsn() * 100 / component.getLifeLimitCycles();
            worst = worst == null ? byCycles : Math.max(worst, byCycles);
        }
        if (component.getCalendarLimit() != null && component.getInstallDate() != null) {
            long total = ChronoUnit.DAYS.between(component.getInstallDate(), component.getCalendarLimit());
            long used = ChronoUnit.DAYS.between(component.getInstallDate(), LocalDate.now(ZoneOffset.UTC));
            if (total > 0) {
                int byCalendar = (int) Math.min(999, used * 100 / total);
                worst = worst == null ? byCalendar : Math.max(worst, byCalendar);
            }
        }
        return worst;
    }

    @Override
    public List<DocumentDto> findDocuments(UUID tenantId, String category) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return documentRepository.findLibrary(tenantId, blankToNull(category)).stream()
                .map(document -> toDto(document, today))
                .toList();
    }

    private DocumentDto toDto(CamoDocument document, LocalDate today) {
        Long days = document.getExpiryDate() == null
                ? null
                : ChronoUnit.DAYS.between(today, document.getExpiryDate());

        /* No expiry is a fact about the document, not a gap in the record. An
           AMM does not expire, and saying so is different from saying nothing.

           And a superseded document is MEANT to be past its date: the ARC from
           two years ago expired exactly as it was supposed to. Judging it as
           expired would bury the one certificate that actually matters under a
           list of the ones it replaced. */
        String status;
        if (!"CURRENT".equals(document.getStatus())) {
            status = document.getStatus();
        } else if (days == null) {
            status = "NO_EXPIRY";
        } else if (days < 0) {
            status = "EXPIRED";
        } else if (days <= HORIZON_DAYS) {
            status = "EXPIRING";
        } else {
            status = "CURRENT";
        }

        return new DocumentDto(
                document.getId(),
                document.getCategory(),
                document.getTitle(),
                document.getReference(),
                document.getAircraft() == null ? null : document.getAircraft().getId(),
                document.getAircraft() == null ? null : document.getAircraft().getRegistration(),
                document.getIssueDate(),
                document.getExpiryDate(),
                days,
                status,
                document.getIssuedBy(),
                document.getStatus(),
                document.getNotes());
    }

    /**
     * Who may change the record.
     *
     * <p>The authority is read from the CAMO role rather than stored beside it:
     * one fact, one place. Moving somebody out of the department takes their
     * authority with them, which is the only behaviour that is ever correct.
     */
    @Override
    public List<UserDto> findUsers(UUID tenantId) {
        return userRepository.findByTenantIdOrderByDisplayName(tenantId).stream()
                .map(user -> new UserDto(
                        user.getId(), user.getLogin(), user.getDisplayName(),
                        user.getRole(), user.getCamoRole(), user.getEmail(),
                        INSIDE_CAMO.contains(user.getCamoRole()),
                        user.isActive()))
                .toList();
    }

    @Override
    public List<AuditEventDto> findAuditTrail(UUID tenantId, int limit) {
        return auditRepository
                .findByTenantIdOrderByAtDesc(tenantId, PageRequest.of(0, Math.min(limit, 500)))
                .stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(AuditEventDto::at).reversed())
                .toList();
    }

    private AuditEventDto toDto(AuditEvent event) {
        return new AuditEventDto(
                event.getId(), event.getAt(), event.getEntity(), event.getEntityId(),
                event.getEntityLabel(), event.getAction(), event.getField(),
                event.getOldValue(), event.getNewValue(),
                event.getActorName(), event.getActorRole(), event.getReason());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
