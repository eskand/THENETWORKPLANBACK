package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.dto.CancelLegCommand;
import com.thenetworkplan.networkplan.ops.dto.ChangeAircraftCommand;
import com.thenetworkplan.networkplan.ops.dto.CreateLegCommand;
import com.thenetworkplan.networkplan.ops.dto.LegDelayDto;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.dto.MoveLegCommand;
import com.thenetworkplan.networkplan.ops.dto.SetSlotCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API1 / API2 — the programme and the commands that change it. */
public interface LegService {

    /**
     * How many legs departed from, and arrived at, each station since a date.
     *
     * <p>Exposed for the aerodrome directory: DOM8 asks DOM1 how much a station
     * is used instead of reading {@code ops.legs} itself.
     *
     * @return two maps, keyed {@code "DEP"} and {@code "ARR"}, each ICAO to count
     */
    Map<String, Map<String, Long>> countStationUsage(UUID tenantId, LocalDate since);

    /** Every leg whose STD falls on the given day, UTC. */
    List<LegDto> findProgramme(UUID tenantId, LocalDate date);

    /**
     * The programme over a window, for reporting.
     *
     * <p>Exposed so that DOM1 answers "what flew between these two dates"
     * instead of the reporting module reading {@code ops.legs} itself.
     */
    List<LegDto> findProgrammeRange(UUID tenantId, LocalDate from, LocalDate to);

    /**
     * Every coded delay recorded against a leg departing inside the window.
     *
     * <p>One row per delay record, not per leg: a sector held twice for two
     * different reasons carries two, and folding them here would lose the one
     * the operator is asked about.
     */
    List<LegDelayDto> findDelays(UUID tenantId, LocalDate from, LocalDate to);

    /** Delay minutes and occurrences per IATA code over a window. */
    Map<String, long[]> countDelaysByCode(UUID tenantId, LocalDate from, LocalDate to);

    LegDto findById(UUID tenantId, UUID legId);

    LegDto create(UUID tenantId, CreateLegCommand command, UUID actorId);

    LegDto move(UUID tenantId, UUID legId, MoveLegCommand command, UUID actorId);

    LegDto changeAircraft(UUID tenantId, UUID legId, ChangeAircraftCommand command, UUID actorId);

    LegDto cancel(UUID tenantId, UUID legId, CancelLegCommand command, UUID actorId);

    /**
     * Enregistre le creneau ATC recu pour l'etape — {@code occSetSlot()} de
     * l'annexe (prototype l. 14073).
     *
     * <p>Un CTOT ne deplace PAS l'horaire : il deplace l'estimation. La STD
     * publiee reste ce qu'elle etait — c'est elle contre laquelle la ponctualite
     * se mesure — et seul l'ETD suit le creneau. Quand le creneau passe apres le
     * depart estime, l'ecart est enregistre comme un retard code 81 (ATFM) et se
     * propage a la rotation suivante par le meme chemin que tous les autres
     * retards.
     */
    LegDto setSlot(UUID tenantId, UUID legId, SetSlotCommand command, UUID actorId);
}
