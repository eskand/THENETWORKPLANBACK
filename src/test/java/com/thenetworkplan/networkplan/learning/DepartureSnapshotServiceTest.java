package com.thenetworkplan.networkplan.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.config.LearningProperties;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.flightfollowing.service.LegRiskAssessor;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import com.thenetworkplan.networkplan.learning.domain.DepartureSnapshot;
import com.thenetworkplan.networkplan.learning.repository.DepartureSnapshotRepository;
import com.thenetworkplan.networkplan.learning.service.impl.DepartureSnapshotServiceImpl;
import com.thenetworkplan.networkplan.ops.domain.DelayRecord;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.repository.DelayRecordRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import com.thenetworkplan.networkplan.weather.domain.WeatherObservation;
import com.thenetworkplan.networkplan.weather.repository.WeatherObservationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * La « photo a H-1 » : ce que l'on savait d'un vol une heure avant son depart,
 * fige a cet instant et jamais recalcule, puis complete par le resultat.
 *
 * <p>La regle qui decide de tout est le point-in-time : le retard de la jambe
 * precedente n'entre que si l'avion etait deja pose a l'instant de la photo, le
 * METAR retenu est le dernier publie avant cet instant (perime au-dela de deux
 * heures), et rien n'est relu apres coup.
 */
class DepartureSnapshotServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 21, 7, 0, 0, 0, ZoneOffset.UTC);

    private final LegRepository legRepository = mock(LegRepository.class);
    private final DepartureSnapshotRepository snapshots = mock(DepartureSnapshotRepository.class);
    private final DelayRecordRepository delayRecords = mock(DelayRecordRepository.class);
    private final WeatherObservationRepository weather = mock(WeatherObservationRepository.class);
    private final PermitService permitService = mock(PermitService.class);
    private final GroundServiceService groundServiceService = mock(GroundServiceService.class);
    private final CrewAssignmentService crewAssignmentService = mock(CrewAssignmentService.class);
    private final AircraftService aircraftService = mock(AircraftService.class);
    private final LegRiskAssessor legRiskAssessor = mock(LegRiskAssessor.class);

    private final DepartureSnapshotServiceImpl service = new DepartureSnapshotServiceImpl(
            legRepository, snapshots, delayRecords, weather, permitService, groundServiceService,
            crewAssignmentService, aircraftService, legRiskAssessor, new LearningProperties(), new OpsProperties());

    private final Aircraft alpha = aircraft("TS-NPA", "F2TH");
    private final Aircraft bravo = aircraft("TS-NPB", "C25B");

    /** La jambe precedente de TS-NPA : posee a 06:42 pour une STA a 06:30. */
    private final Leg previous = leg(alpha, "TNP100", "LFMN", "DTTA", NOW.minusHours(3), NOW.minusMinutes(30));
    /** Le vol photographie : STD a 08:00, soit une heure apres NOW. */
    private final Leg subject = leg(alpha, "TNP101", "DTTA", "LFMN", NOW.plusHours(1), NOW.plusHours(3));
    /** Un autre depart de DTTA vingt minutes plus tard (congestion au depart). */
    private final Leg neighbour = leg(bravo, "TNP202", "DTTA", "LIRF", NOW.plusMinutes(80), NOW.plusHours(3).plusMinutes(25));

    @Test
    @DisplayName("takeDue photographie le vol dont le STD est a une heure, avec la rotation, la congestion, les faits et la meteo connus a cet instant")
    void takesTheSnapshotAtLeadTime() {
        previous.setOnAt(NOW.minusMinutes(18)); // 06:42 : 12 min apres la STA 06:30
        stubProgramme(previous, subject, neighbour);
        when(snapshots.existsByTenantIdAndLegId(TENANT, subject.getId())).thenReturn(false);
        when(permitService.summariseByLegIds(eq(TENANT), anyCollection()))
                .thenReturn(Map.of(subject.getId(), new LegPermitsSummary(subject.getId(), 2, 1)));
        when(groundServiceService.summariseByLegIds(eq(TENANT), anyCollection()))
                .thenReturn(Map.of(subject.getId(), new LegServicesSummary(subject.getId(), 4, 3, "PENDING")));
        when(crewAssignmentService.findByLegIds(eq(TENANT), anyCollection(), any()))
                .thenReturn(Map.of(subject.getId(), new LegCrewDto(subject.getId(), List.of(), 2, 2, true, "OK", "VALID")));
        when(aircraftService.findOpenMelByAircraft(TENANT))
                .thenReturn(Map.of(alpha.getId(), List.of(new MelItemDto(UUID.randomUUID(), "MEL 21-51-01", "C",
                        "APU", null, NOW.minusDays(2), NOW.plusDays(8), false))));
        when(legRiskAssessor.assess(any(), any()))
                .thenReturn(new SmsRiskRule.Assessment("MEDIUM", 3, 2, 6, "Monitor and reassess.", List.of(), List.of()));
        when(weather.findRecent(anyCollection(), any())).thenReturn(List.of(
                metar("DTTA", NOW.minusMinutes(10), 240, 12, 9999, null, true),
                metar("DTTA", NOW.minusMinutes(40), 230, 10, 9999, null, true),
                metar("LFMN", NOW.minusMinutes(150), 90, 5, 6000, 1200, false)));

        int taken = service.takeDue(TENANT, NOW);

        assertThat(taken).isEqualTo(1);
        ArgumentCaptor<DepartureSnapshot> saved = ArgumentCaptor.forClass(DepartureSnapshot.class);
        verify(snapshots).save(saved.capture());
        DepartureSnapshot s = saved.getValue();

        assertThat(s.getLegId()).isEqualTo(subject.getId());
        assertThat(s.getTakenAt()).isEqualTo(NOW);
        assertThat(s.getLeadMinutes()).isEqualTo(60);
        assertThat(s.getFlightNo()).isEqualTo("TNP101");
        assertThat(s.getRegistration()).isEqualTo("TS-NPA");
        assertThat(s.getIcaoType()).isEqualTo("F2TH");

        // rotation : escale programmee 90 min, jambe precedente posee 12 min tard
        assertThat(s.getLegIndex()).isEqualTo(2);
        assertThat(s.getSchedTurnaroundMin()).isEqualTo(90);
        assertThat(s.isInboundKnown()).isTrue();
        assertThat(s.getInboundDelayMin()).isEqualTo(12);

        // congestion : un autre depart de DTTA a +20 min, aucune autre arrivee a LFMN
        assertThat(s.getDepCongestion()).isEqualTo(1);
        assertThat(s.getArrCongestion()).isZero();

        // les faits que VIGIL lit
        assertThat(s.getPermitsTotal()).isEqualTo(2);
        assertThat(s.getPermitsOutstanding()).isEqualTo(1);
        assertThat(s.getServicesTotal()).isEqualTo(4);
        assertThat(s.getServicesConfirmed()).isEqualTo(3);
        assertThat(s.getServicesReadiness()).isEqualTo("PENDING");
        assertThat(s.getCrewComplete()).isTrue();
        assertThat(s.getCrewFtlStatus()).isEqualTo("OK");
        assertThat(s.getMelOpen()).isEqualTo(1);
        assertThat(s.isMelBlocking()).isFalse();
        assertThat(s.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(s.getRiskIndex()).isEqualTo(6);

        // meteo : le dernier METAR de DTTA avant NOW (06:50), pas celui de 06:20 ; LFMN perime (2 h 30) -> rien
        assertThat(s.getDepWxObservedAt()).isEqualTo(NOW.minusMinutes(10));
        assertThat(s.getDepWxAgeMin()).isEqualTo(10);
        assertThat(s.getDepWindDirDeg()).isEqualTo(240);
        assertThat(s.getDepWindKt()).isEqualTo(12);
        assertThat(s.getDepCavok()).isTrue();
        assertThat(s.getArrWxObservedAt()).isNull();
        assertThat(s.getArrWindKt()).isNull();

        // le resultat n'est pas encore connu
        assertThat(s.getOutcomeAt()).isNull();
        assertThat(s.getTargetDelay15()).isNull();
    }

    @Test
    @DisplayName("la jambe precedente encore en vol a l'instant de la photo ne donne pas de retard entrant : inbound_known = 0")
    void inboundUnknownWhenPreviousLegHasNotLanded() {
        previous.setOnAt(null); // pas posee a 07:00
        stubProgramme(previous, subject);
        stubEmptyFacts();

        service.takeDue(TENANT, NOW);

        ArgumentCaptor<DepartureSnapshot> saved = ArgumentCaptor.forClass(DepartureSnapshot.class);
        verify(snapshots).save(saved.capture());
        assertThat(saved.getValue().isInboundKnown()).isFalse();
        assertThat(saved.getValue().getInboundDelayMin()).isNull();
        assertThat(saved.getValue().getSchedTurnaroundMin()).isEqualTo(90);
    }

    @Test
    @DisplayName("un vol deja photographie ne l'est pas deux fois ; un vol hors de la fenetre H-1 attend son tour")
    void takesEachLegOnce() {
        stubProgramme(subject, neighbour);
        stubEmptyFacts();
        when(snapshots.existsByTenantIdAndLegId(TENANT, subject.getId())).thenReturn(true);

        assertThat(service.takeDue(TENANT, NOW)).isZero();
        verify(snapshots, never()).save(any());
    }

    @Test
    @DisplayName("settleOutcomes complete la photo avec le retard reel, la cible a 15 min et le code saisi ; un vol annule est marque tel")
    void settlesOutcomes() {
        DepartureSnapshot open = snapshotOf(subject);
        subject.setOutAt(subject.getStd().plusMinutes(22));
        Leg cancelled = leg(bravo, "TNP303", "DTTA", "LIRF", NOW.plusHours(2), NOW.plusHours(4));
        cancelled.setStatus(LegStatus.CANCELLED);
        DepartureSnapshot openCancelled = snapshotOf(cancelled);
        Leg stillWaiting = leg(bravo, "TNP404", "DTTA", "LIRF", NOW.plusHours(5), NOW.plusHours(7));
        DepartureSnapshot openWaiting = snapshotOf(stillWaiting);

        when(snapshots.findByTenantIdAndOutcomeAtIsNull(TENANT)).thenReturn(List.of(open, openCancelled, openWaiting));
        when(legRepository.findAllById(anyCollection())).thenReturn(List.of(subject, cancelled, stillWaiting));
        DelayRecord code = new DelayRecord();
        code.setLegId(subject.getId());
        code.setCode("93");
        code.setMinutes(22);
        when(delayRecords.findByTenantIdAndLegIdIn(eq(TENANT), anyCollection())).thenReturn(List.of(code));

        int settled = service.settleOutcomes(TENANT, NOW.plusHours(2));

        assertThat(settled).isEqualTo(2);
        assertThat(open.getOutAt()).isEqualTo(subject.getStd().plusMinutes(22));
        assertThat(open.getDepDelayMin()).isEqualTo(22);
        assertThat(open.getTargetDelay15()).isTrue();
        assertThat(open.getDelayCode()).isEqualTo("93");
        assertThat(open.getOutcomeAt()).isEqualTo(NOW.plusHours(2));
        assertThat(openCancelled.isCancelled()).isTrue();
        assertThat(openCancelled.getOutcomeAt()).isEqualTo(NOW.plusHours(2));
        assertThat(openWaiting.getOutcomeAt()).isNull();
    }

    /* ───────────────────────────── fixtures ───────────────────────────── */

    private void stubProgramme(Leg... legs) {
        when(legRepository.findProgramme(eq(TENANT), any(), any())).thenReturn(List.of(legs));
    }

    private void stubEmptyFacts() {
        when(snapshots.existsByTenantIdAndLegId(eq(TENANT), any())).thenReturn(false);
        when(permitService.summariseByLegIds(eq(TENANT), anyCollection())).thenReturn(Map.of());
        when(groundServiceService.summariseByLegIds(eq(TENANT), anyCollection())).thenReturn(Map.of());
        when(crewAssignmentService.findByLegIds(eq(TENANT), anyCollection(), any())).thenReturn(Map.of());
        when(aircraftService.findOpenMelByAircraft(TENANT)).thenReturn(Map.of());
        when(legRiskAssessor.assess(any(), any()))
                .thenReturn(new SmsRiskRule.Assessment("LOW", 1, 1, 1, "Acceptable risk.", List.of(), List.of()));
        when(weather.findRecent(anyCollection(), any())).thenReturn(List.of());
    }

    private static Aircraft aircraft(String registration, String icaoType) {
        AircraftType type = new AircraftType();
        type.setIcaoType(icaoType);
        Aircraft aircraft = new Aircraft();
        aircraft.setId(UUID.randomUUID());
        aircraft.setTenantId(TENANT);
        aircraft.setRegistration(registration);
        aircraft.setAircraftType(type);
        return aircraft;
    }

    private static Leg leg(Aircraft aircraft, String flightNo, String dep, String arr,
                           OffsetDateTime std, OffsetDateTime sta) {
        Leg leg = new Leg();
        leg.setId(UUID.randomUUID());
        leg.setTenantId(TENANT);
        leg.setAircraft(aircraft);
        leg.setFlightNo(flightNo);
        leg.setDepIcao(dep);
        leg.setArrIcao(arr);
        leg.setStd(std);
        leg.setSta(sta);
        leg.setStatus(LegStatus.PLANNED);
        return leg;
    }

    private static DepartureSnapshot snapshotOf(Leg leg) {
        DepartureSnapshot s = new DepartureSnapshot();
        s.setTenantId(TENANT);
        s.setLegId(leg.getId());
        s.setStd(leg.getStd());
        s.setSta(leg.getSta());
        return s;
    }

    private static WeatherObservation metar(String station, OffsetDateTime observedAt, int dir, int kt,
                                            int visibilityM, Integer ceilingFt, boolean cavok) {
        WeatherObservation o = new WeatherObservation();
        o.setStationIcao(station);
        o.setObservedAt(observedAt);
        o.setWindDirDeg(dir);
        o.setWindSpeedKt(kt);
        o.setVisibilityM(visibilityM);
        o.setCeilingFt(ceilingFt);
        o.setCavok(cavok);
        o.setRawText(station + " METAR");
        o.setProvider("NOAA");
        return o;
    }
}
