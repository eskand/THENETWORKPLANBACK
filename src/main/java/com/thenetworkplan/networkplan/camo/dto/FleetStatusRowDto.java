package com.thenetworkplan.networkplan.camo.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One registration on the CAMO screen.
 *
 * <p>TSN and CSN are the stored counters, and {@code blockMinutes28d} is what
 * the aircraft actually flew over the last four weeks, summed from
 * {@code camo.utilisation}. The two together are the answer to the audit's
 * "counters never fed": a counter that never moves next to a utilisation that
 * does is a visible contradiction.
 *
 * <p>The airworthiness review fields answer a question the due list cannot: a
 * check that is due keeps an aircraft on the ground for days, an expired
 * certificate keeps it there until an authority acts. {@code arcDaysLeft} is
 * measured against the day the row is built, so it is never one of the frozen
 * counters the audit found.
 */
public record FleetStatusRowDto(
        UUID aircraftId,
        String registration,
        String icaoType,
        /** Le modele publie, « Falcon 900LX » : ce que le tableau met en infobulle. */
        String model,
        /** FALCON, CITATION, LEGACY — la granularite a laquelle la flotte se filtre. */
        String typeFamily,
        String status,
        String statusReason,
        BigDecimal hoursSinceNew,
        Integer cyclesSinceNew,
        long blockMinutes28d,
        long cycles28d,
        long flights28d,
        String nextDueCode,
        LocalDate nextDueOn,
        Long nextDueInDays,
        String worstDueStatus,
        int openTasks,
        int overdueTasks,
        int openMelItems,
        int openDefects,
        int openDirectives,
        String arcCertificateNo,
        LocalDate arcExpiresOn,
        Long arcDaysLeft,
        String arcVerdict,
        /** Parts under fifteen per cent of their life — the ones that ground without warning. */
        int criticalLlps,
        /** The nearest part's remaining life, or null when the registration has none on file. */
        Integer worstLlpPercent,
        int openWorkOrders) implements Serializable {
}
