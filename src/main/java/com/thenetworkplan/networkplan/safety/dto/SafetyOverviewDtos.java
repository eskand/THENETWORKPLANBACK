package com.thenetworkplan.networkplan.safety.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The read models of the Safety Manager dashboard. */
public final class SafetyOverviewDtos {

    private SafetyOverviewDtos() {
    }

    /**
     * One safety performance indicator with its measured value.
     *
     * <p>{@code value} is null when nothing can measure it yet, and that is not
     * the same as zero: an occurrence rate of zero means no occurrences, an
     * unmeasurable one means no exposure figure to divide by.
     */
    public record SpiDto(
            String code,
            String name,
            String unit,
            String domain,
            BigDecimal value,
            BigDecimal target,
            BigDecimal alert,
            String direction,
            boolean meetsTarget,
            boolean breachesAlert,
            String computedBy) implements Serializable {
    }

    /** One audit of the programme, with its findings counted. */
    public record AuditDto(
            UUID id,
            String reference,
            String name,
            String standard,
            String scope,
            String auditor,
            boolean externalAudit,
            LocalDate plannedOn,
            LocalDate conductedOn,
            LocalDate closedOn,
            String status,
            Short scorePercent,
            int findings,
            int openFindings) implements Serializable {
    }

    public record AuditFindingDto(
            UUID id,
            String reference,
            String auditReference,
            String level,
            String title,
            String detail,
            String requirement,
            LocalDate raisedOn,
            LocalDate dueOn,
            LocalDate closedOn,
            boolean overdue) implements Serializable {
    }

    public record InvestigationDto(
            UUID id,
            String reference,
            String occurrenceReference,
            String title,
            String investigatorName,
            LocalDate openedOn,
            LocalDate targetOn,
            LocalDate closedOn,
            String status,
            String method,
            String rootCause,
            String contributingFactors,
            boolean overdue,
            /** Declared by the investigator, 0 to 100. Never derived from dates. */
            int progressPercent,
            /**
             * The five-why chain, in order: the first link is what happened, the
             * last is the organisational condition that allowed it — and the last
             * is the one a corrective action has to attack.
             */
            List<String> steps,
            /** Safety recommendations arising from the analysis, in order. */
            List<String> recommendations) implements Serializable {
    }

    public record SafetyChangeDto(
            UUID id,
            String reference,
            String title,
            String description,
            String domain,
            LocalDate raisedOn,
            LocalDate effectiveOn,
            String status,
            String ownerName,
            Short initialIndex,
            Short residualIndex,
            String mitigation,
            boolean active) implements Serializable {
    }

    /**
     * One live finding produced by the safety scan.
     *
     * <p>Not stored. The scan reads the operational modules — crew documents,
     * qualifications, airworthiness review certificates, maintenance limits,
     * deferred defects, aerodrome notes — and reports what it finds at that
     * instant. A stored finding would go on asserting a licence expiry that
     * was renewed yesterday.
     */
    public record MonitoringFindingDto(
            String id,
            String domain,
            String severity,
            String subject,
            String detail,
            /**
             * What the operator has to do about it, in the imperative.
             *
             * <p>A finding without a required action is an observation, and the
             * prototype prints the action in italics under every line because a
             * monitoring screen that only names problems makes the reader invent
             * the remedy.
             */
            String requiredAction,
            /** The route of the module that owns it, so the row can open it. */
            String route) implements Serializable {
    }

    /** The scan's result: the findings, and how many of each severity. */
    public record MonitoringSummaryDto(
            OffsetDateTime scannedAt,
            int total,
            int critical,
            int high,
            int medium,
            int low,
            List<MonitoringFindingDto> findings,
            List<DomainCountDto> byDomain) implements Serializable {
    }

    public record DomainCountDto(
            String domain,
            String label,
            int total,
            int critical,
            int high,
            int medium,
            int low) implements Serializable {
    }

    /** The occurrence risk profile: how many sit in each index band. */
    public record RiskProfileDto(
            int recorded,
            int intolerable,
            int high,
            int tolerable,
            int unassessed) implements Serializable {
    }

    /** One month of the six-month trend. */
    public record TrendPointDto(
            String month,
            int occurrences,
            int highAndIntolerable) implements Serializable {
    }

    /** Initial against residual risk, per domain, for the register. */
    public record RiskByDomainDto(
            String domain,
            String label,
            int initialIndex,
            int residualIndex) implements Serializable {
    }

    /** Who answers for the safety management system. */
    /**
     * The published roster, checked against the flight-time limitations.
     *
     * <p>The annexe opens the Safety Overview with this line because it is the
     * one an accountable manager reads first: whether the roster crews are
     * actually flying is legal. A nil return is only as good as the roster
     * behind it, so {@code dutiesChecked} travels with it — « no exceedance
     * across nothing » is not an assurance.
     *
     * @param months       the calendar months covered, as an operator names them
     * @param exceedances  how many breaches the FTL engine found
     * @param crewAffected the crew members concerned, at most four
     */
    public record RosterCheckDto(
            List<String> months,
            int daysChecked,
            int dutiesChecked,
            int exceedances,
            List<String> crewAffected) implements Serializable {
    }

    public record AccountabilityDto(
            String accountableManager,
            String safetyManager,
            String operator,
            String aocReference,
            int activeChanges,
            OffsetDateTime lastScanAt) implements Serializable {
    }

    /** The whole Safety Manager overview in one response. */
    public record SafetyOverviewDto(
            int occurrencesThisMonth,
            int occurrencesStillOpen,
            int risksAboveTolerance,
            int overdueActions,
            int openAuditFindings,
            int auditsPlanned,
            MonitoringSummaryDto monitoring,
            RiskProfileDto riskProfile,
            List<TrendPointDto> trend,
            List<SpiDto> indicators,
            List<SafetyDtos.OccurrenceDto> recentOccurrences,
            List<SafetyDtos.ActionDto> correctiveActions,
            List<AuditDto> auditProgramme,
            List<RiskByDomainDto> riskByDomain,
            /** Les enquetes ouvertes et closes : l onglet Investigations les lit. */
            List<InvestigationDto> investigations,
            /** Les changements sous gestion du changement (annexe 19, composante 3). */
            List<SafetyChangeDto> changes,
            /** Le roster publie, passe au moteur FTL : la premiere ligne de l ecran. */
            RosterCheckDto rosterCheck,
            AccountabilityDto accountability) implements Serializable {
    }
}
