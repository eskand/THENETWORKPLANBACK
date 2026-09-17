package com.thenetworkplan.networkplan.dispatch.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One line of the dispatch board, flattened for the table.
 *
 * <p>Station codes come in both forms: {@code depIcao} is the key the rest of the
 * system uses, {@code depCode} is the IATA code an operator reads. The prototype
 * stored one field and put whichever it had into it.
 */
public record DispatchRowDto(
        DispatchRowKind kind,
        UUID rowId,
        UUID legId,
        UUID aircraftId,

        String flightNo,
        String label,
        String riskLevel,

        String registration,
        String icaoType,
        String model,

        String depIcao,
        String depCode,
        String arrIcao,
        String arrCode,
        String routeLabel,
        String baseIcao,

        OffsetDateTime std,
        OffsetDateTime etd,
        OffsetDateTime atd,
        OffsetDateTime sta,
        OffsetDateTime eta,
        OffsetDateTime ata,
        OffsetDateTime ctot,

        String servicesReadiness,
        int servicesConfirmed,
        int servicesTotal,

        int permitsOutstanding,

        boolean crewAssigned,
        int crewSeatsFilled,
        int crewMinimumSeats,
        String crewFtlStatus,
        String crewDocumentStatus,

        String status,
        String statusTone,
        boolean attention,
        boolean melBlocking,
        int delayMinutes,
        String note,
        /**
         * The aerodrome as an operator names it, for the flight file.
         *
         * <p>« Tunis Carthage (DTTA) » rather than « DTTA »: the file is read
         * away from the board, printed and attached to a trip folder, and four
         * letters are not enough to check somebody has the right aerodrome.
         */
        String depName,
        /**
         * The town the aerodrome serves, ISO 3166-1 alpha-2 beside it.
         *
         * <p>The route card in the file names the city, not the aerodrome: a
         * customer reads « Tunis », a dispatcher reads « Tunis Carthage
         * (DTTA) », and the file carries both rather than letting one stand
         * for the other.
         */
        String depCity,
        String depCountry,
        String arrName,
        String arrCity,
        String arrCountry,
        /** PAX, FERRY … the operation type, as Sales set it on the leg. */
        String flightType,
        /** SCHEDULED / NON_SCHEDULED / PRIVATE / STATE — la nature commerciale. */
        String commercialType,
        /** La lettre de la case 8 du plan de vol OACI (S/N/G/X). */
        String flightPlanLetter,
        int paxCount,
        /**
         * The SMS index behind {@link #riskLevel()}, one to twenty-five.
         *
         * <p>ICAO Doc 9859's matrix is severity times likelihood, and the
         * responsible manager reads the cell, not the colour: MEDIUM at 6 and
         * MEDIUM at 12 do not call for the same answer. The file prints both,
         * as the annexe does.
         */
        int riskIndex,
        /** The factor that carries the index — « EXPIRED crew document ». */
        String riskTop,
        /** What the matrix expects at that level, in one clause. */
        String riskAction,
        /**
         * When the movement message went out, or null if it has not.
         *
         * <p>The file offers « Send MVT » either way — a station that lost the
         * first one asks for another — but a dispatcher has to be able to see
         * that one already left before sending a second.
         */
        OffsetDateTime mvtSentAt) implements Serializable {
}
