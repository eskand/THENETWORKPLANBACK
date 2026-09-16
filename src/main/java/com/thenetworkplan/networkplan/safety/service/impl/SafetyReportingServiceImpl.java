package com.thenetworkplan.networkplan.safety.service.impl;

import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.safety.domain.Occurrence;
import com.thenetworkplan.networkplan.safety.domain.OccurrenceStatus;
import com.thenetworkplan.networkplan.safety.domain.ReportDraft;
import com.thenetworkplan.networkplan.safety.domain.ReportType;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.DraftDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.MyReportDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.ReportTypeDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.ReportingBoardDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.SaveDraftCommand;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.SubmitReportCommand;
import com.thenetworkplan.networkplan.safety.repository.OccurrenceRepository;
import com.thenetworkplan.networkplan.safety.repository.ReportDraftRepository;
import com.thenetworkplan.networkplan.safety.repository.SafetyActionRepository;
import com.thenetworkplan.networkplan.safety.service.SafetyReportingService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safety Reports — the reporter's side of the safety management system.
 *
 * <p><b>Who this screen is for.</b> Not the Safety Manager: anybody in the
 * company. Flight and cabin crew, dispatch, OCC, maintenance, ground
 * operations. Its measure of success is how many reports arrive, which is why
 * only three fields are mandatory and why a draft can be left half-written.
 *
 * <p><b>Anonymous and confidential are not the same thing</b>, and the
 * difference is the whole of a just culture. Anonymous: nobody knows who
 * reported, including the Safety Manager, and nobody can come back for a
 * detail. Confidential: the Safety Manager knows and de-identifies before
 * sharing any analysis. The first protects absolutely and costs the follow-up
 * question; the second keeps the conversation open. A reporter has to be able
 * to choose, so the form offers both and says what each means.
 */
@Service
@Transactional(readOnly = true)
public class SafetyReportingServiceImpl implements SafetyReportingService {

    /** The phases the form offers, in the order a flight goes through them. */
    private static final List<String> PHASES = List.of(
            "Pre-flight", "Taxi out", "Take-off", "Initial climb", "Climb", "Cruise",
            "Descent", "Approach", "Landing", "Taxi in", "Post-flight", "Ground handling",
            "Not applicable");

    private final OccurrenceRepository occurrenceRepository;
    private final ReportDraftRepository draftRepository;
    private final SafetyActionRepository actionRepository;
    private final PersonRepository personRepository;
    private final AircraftRepository aircraftRepository;

    public SafetyReportingServiceImpl(OccurrenceRepository occurrenceRepository,
                                      ReportDraftRepository draftRepository,
                                      SafetyActionRepository actionRepository,
                                      PersonRepository personRepository,
                                      AircraftRepository aircraftRepository) {
        this.occurrenceRepository = occurrenceRepository;
        this.draftRepository = draftRepository;
        this.actionRepository = actionRepository;
        this.personRepository = personRepository;
        this.aircraftRepository = aircraftRepository;
    }

    @Override
    public ReportingBoardDto findBoard(UUID tenantId, UUID reporterId) {
        List<DraftDto> drafts = reporterId == null
                ? List.of()
                : draftRepository.findOpenByAuthor(tenantId, reporterId).stream()
                        .map(this::toDto).toList();

        List<MyReportDto> mine = reporterId == null
                ? List.of()
                : occurrenceRepository.findAll().stream()
                        .filter(occurrence -> tenantId.equals(occurrence.getTenantId()))
                        .filter(occurrence -> occurrence.getReportedBy() != null
                                && reporterId.equals(occurrence.getReportedBy().getId()))
                        .sorted((a, b) -> b.getReportedAt().compareTo(a.getReportedAt()))
                        .map(this::toDto)
                        .toList();

        return new ReportingBoardDto(
                Arrays.stream(ReportType.values()).map(this::toDto).toList(),
                drafts,
                mine,
                PHASES,
                aircraftRepository.findFleet(tenantId).stream()
                        .map(aircraft -> aircraft.getRegistration())
                        .sorted()
                        .toList(),
                justCulture(),
                mine.stream().mapToInt(MyReportDto::openActions).sum());
    }

    /**
     * The operator's own wording on how a report is handled.
     *
     * <p>Published text, not decoration: it is what the operator commits to and
     * may have to defend. It is stated here rather than in the browser so that
     * one wording reaches every screen that shows it.
     */
    private String justCulture() {
        return "A confidential report is de-identified by the Safety Manager before any analysis "
                + "is shared. Reporting an honest error will never of itself lead to disciplinary "
                + "action. Wilful violations and destructive acts remain outside the protection of "
                + "the policy.";
    }

    /* ---------- drafts ---------- */

    @Override
    @Transactional
    public DraftDto saveDraft(UUID tenantId, UUID reporterId, SaveDraftCommand command) {
        if (reporterId == null) {
            throw new BusinessRuleException("REPORTER_REQUIRED",
                    "A draft belongs to its author; the request carries no reporter");
        }
        Person author = personRepository.findByTenantIdAndId(tenantId, reporterId)
                .orElseThrow(() -> ResourceNotFoundException.of("Person", reporterId));

        ReportDraft draft;
        if (command.draftId() == null) {
            draft = new ReportDraft();
            draft.setTenantId(tenantId);
            draft.setAuthorId(reporterId);
            draft.setAuthorName(author.fullName());
        } else {
            draft = draftRepository.findByTenantIdAndId(tenantId, command.draftId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Draft", command.draftId()));
            // A draft is its author's. Not filtered after the fact: refused.
            if (!reporterId.equals(draft.getAuthorId())) {
                throw new BusinessRuleException("DRAFT_NOT_YOURS",
                        "A draft can only be opened by the person who started it");
            }
            if (draft.isSubmitted()) {
                throw new BusinessRuleException("DRAFT_ALREADY_SENT",
                        "That draft has been sent; start a new report rather than editing it");
            }
        }

        apply(draft, command);
        return toDto(draftRepository.save(draft));
    }

    private void apply(ReportDraft draft, SaveDraftCommand command) {
        draft.setReportType(ReportType.of(command.reportType()).orElse(null));
        draft.setTitle(trim(command.title()));
        draft.setOccurredAt(command.occurredAt());
        draft.setPhaseOfFlight(trim(command.phaseOfFlight()));
        draft.setStationIcao(upper(command.stationIcao()));
        draft.setFlightNo(upper(command.flightNo()));
        draft.setRegistration(upper(command.registration()));
        draft.setNarrative(trim(command.narrative()));
        draft.setImmediateAction(trim(command.immediateAction()));
        draft.setReporterSuggestion(trim(command.reporterSuggestion()));
        boolean anonymous = Boolean.TRUE.equals(command.anonymous());
        draft.setAnonymous(anonymous);
        // Anonymous implies confidential: nobody can de-identify what nobody
        // can identify. Set here as well as enforced by the database, so the
        // draft the reporter reads back says what will actually happen.
        draft.setConfidential(anonymous || Boolean.TRUE.equals(command.confidential()));
    }

    @Override
    @Transactional
    public void deleteDraft(UUID tenantId, UUID reporterId, UUID draftId) {
        ReportDraft draft = draftRepository.findByTenantIdAndId(tenantId, draftId)
                .orElseThrow(() -> ResourceNotFoundException.of("Draft", draftId));
        if (reporterId == null || !reporterId.equals(draft.getAuthorId())) {
            throw new BusinessRuleException("DRAFT_NOT_YOURS",
                    "A draft can only be discarded by the person who started it");
        }
        draftRepository.delete(draft);
    }

    /* ---------- submission ---------- */

    /**
     * Sends the report to the Safety Manager.
     *
     * <p>The occurrence is created in {@code REPORTED}: unassessed, which is the
     * truth. Nothing here guesses a risk level — that is the Safety Manager's
     * judgement against the operator's matrix, and a report that arrived
     * pre-scored would bias it.
     */
    @Override
    @Transactional
    public MyReportDto submit(UUID tenantId, UUID reporterId, SubmitReportCommand command) {
        ReportType type = ReportType.of(command.reportType())
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_REPORT_TYPE",
                        command.reportType() + " is not a kind of report this form offers"));

        boolean anonymous = Boolean.TRUE.equals(command.anonymous());

        Occurrence occurrence = new Occurrence();
        occurrence.setTenantId(tenantId);
        occurrence.setReference(nextReference(tenantId));
        occurrence.setReportType(type);
        occurrence.setCategory(type.category());
        occurrence.setTitle(command.title().trim());
        occurrence.setNarrative(command.narrative().trim());
        occurrence.setOccurredAt(command.occurredAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC) : command.occurredAt());
        occurrence.setReportedAt(OffsetDateTime.now(ZoneOffset.UTC));
        occurrence.setPhaseOfFlight(trim(command.phaseOfFlight()));
        occurrence.setStationIcao(upper(command.stationIcao()));
        occurrence.setImmediateAction(trim(command.immediateAction()));
        occurrence.setReporterSuggestion(trim(command.reporterSuggestion()));
        occurrence.setAnonymous(anonymous);
        occurrence.setConfidential(anonymous || Boolean.TRUE.equals(command.confidential()));
        occurrence.setStatus(OccurrenceStatus.REPORTED);

        // An anonymous report carries no reporter. Storing the identity and
        // merely hiding it on screen would be a promise the database breaks.
        if (!anonymous && reporterId != null) {
            personRepository.findByTenantIdAndId(tenantId, reporterId)
                    .ifPresent(occurrence::setReportedBy);
        }

        if (command.registration() != null && !command.registration().isBlank()) {
            aircraftRepository.findFleet(tenantId).stream()
                    .filter(aircraft -> aircraft.getRegistration()
                            .equalsIgnoreCase(command.registration().trim()))
                    .findFirst()
                    .ifPresent(occurrence::setAircraft);
        }

        occurrence.getSource().setType("report");
        occurrence.getSource().setReference("Safety Reports");
        occurrenceRepository.save(occurrence);

        if (command.draftId() != null) {
            draftRepository.findByTenantIdAndId(tenantId, command.draftId()).ifPresent(draft -> {
                // The draft is kept, marked sent: the reporter has to be able to
                // see what they actually submitted, and from which draft.
                draft.setSubmittedOccurrenceId(occurrence.getId());
                draft.setSubmittedAt(OffsetDateTime.now(ZoneOffset.UTC));
                draftRepository.save(draft);
            });
        }

        return toDto(occurrence);
    }

    private String nextReference(UUID tenantId) {
        int year = OffsetDateTime.now(ZoneOffset.UTC).getYear();
        long count = occurrenceRepository.findAll().stream()
                .filter(occurrence -> tenantId.equals(occurrence.getTenantId()))
                .count();
        return "OCC-" + year + "-" + String.format("%04d", count + 1);
    }

    /* ---------- mapping ---------- */

    private ReportTypeDto toDto(ReportType type) {
        return new ReportTypeDto(type.name(), type.label(), type.shortLabel(),
                type.description(), type.category().name(), type.concernsTheReporter());
    }

    private DraftDto toDto(ReportDraft draft) {
        return new DraftDto(draft.getId(),
                draft.getReportType() == null ? null : draft.getReportType().name(),
                draft.getTitle(), draft.getOccurredAt(), draft.getPhaseOfFlight(),
                draft.getStationIcao(), draft.getFlightNo(), draft.getRegistration(),
                draft.getNarrative(), draft.getImmediateAction(), draft.getReporterSuggestion(),
                draft.isAnonymous(), draft.isConfidential(), draft.getUpdatedAt(),
                draft.isSubmittable(), draft.getSubmittedOccurrenceId());
    }

    private MyReportDto toDto(Occurrence occurrence) {
        int open = (int) actionRepository.findAll().stream()
                .filter(action -> action.getOccurrence() != null
                        && action.getOccurrence().getId().equals(occurrence.getId()))
                .filter(action -> !"DONE".equals(action.getStatus().name()))
                .count();

        return new MyReportDto(occurrence.getId(), occurrence.getReference(),
                occurrence.getReportType() == null ? null : occurrence.getReportType().name(),
                occurrence.getReportType() == null ? null : occurrence.getReportType().label(),
                occurrence.getTitle(), occurrence.getOccurredAt(), occurrence.getReportedAt(),
                occurrence.getStatus().name(),
                occurrence.getRiskLevel() == null ? null : occurrence.getRiskLevel().name(),
                occurrence.isAnonymous(), occurrence.isConfidential(), open,
                occurrence.getClosedAt() == null ? null : "Closed " + occurrence.getClosedAt().toLocalDate());
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
