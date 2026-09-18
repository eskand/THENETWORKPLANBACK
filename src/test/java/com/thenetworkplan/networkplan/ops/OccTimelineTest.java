package com.thenetworkplan.networkplan.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.dto.OccEventDto;
import com.thenetworkplan.networkplan.ops.dto.OccTimelineDto;
import com.thenetworkplan.networkplan.ops.dto.ReleaseDto;
import com.thenetworkplan.networkplan.ops.repository.LegEventRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.ReleaseService;
import com.thenetworkplan.networkplan.ops.service.impl.OccTimelineServiceImpl;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesDto;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La frise OCC Dispatch.
 *
 * <p>Les cas interessants sont ceux que l'annexe rate. Chez elle,
 * {@code computeOccTimeline()} deduit les statuts de l'heure qu'il est : une
 * etape dont la fenetre est passee s'affiche « Completed » meme si personne n'a
 * rien fait, et c'est precisement ce qui rend sa frise inutilisable pour
 * decider. Les trois premiers tests verifient qu'ici une fenetre passee sans
 * fait enregistre rend « Delayed », qu'un fait enregistre rend « Completed », et
 * qu'un blocage se propage a ce qui en depend.
 */
class OccTimelineTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LEG_ID = UUID.randomUUID();

    private final LegRepository legRepository = mock(LegRepository.class);
    private final LegEventRepository legEventRepository = mock(LegEventRepository.class);
    private final ReleaseService releaseService = mock(ReleaseService.class);
    private final GroundServiceService groundServiceService = mock(GroundServiceService.class);
    private final CrewAssignmentService crewAssignmentService = mock(CrewAssignmentService.class);

    private final OccTimelineServiceImpl service = new OccTimelineServiceImpl(
            legRepository, legEventRepository, releaseService, groundServiceService,
            crewAssignmentService, new OpsProperties());

    /** Une etape dont la fenetre de depart est largement passee. */
    private Leg pastLeg() {
        Aircraft aircraft = new Aircraft();
        aircraft.setRegistration("TS-NPT");

        Leg leg = new Leg();
        leg.setId(LEG_ID);
        leg.setTenantId(TENANT);
        leg.setAircraft(aircraft);
        leg.setFlightNo("TNP810");
        leg.setDepIcao("DTTA");
        leg.setArrIcao("LFPG");
        leg.setStd(OffsetDateTime.now(ZoneOffset.UTC).minusHours(6));
        leg.setSta(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));
        leg.setStatus(LegStatus.PLANNED);
        return leg;
    }

    private void stub(Leg leg, Optional<ReleaseDto> release, List<ServiceRequestDto> requests) {
        when(legRepository.findOneWithDetails(eq(TENANT), eq(LEG_ID))).thenReturn(Optional.of(leg));
        when(legRepository.findRotation(eq(TENANT), any(), any(), any())).thenReturn(List.of(leg));
        when(legEventRepository.findByTenantIdAndLegIdOrderByCreatedAtDesc(eq(TENANT), eq(LEG_ID)))
                .thenReturn(List.of());
        when(releaseService.findCurrent(eq(TENANT), eq(LEG_ID))).thenReturn(release);
        when(groundServiceService.findByLeg(eq(TENANT), eq(LEG_ID))).thenReturn(
                new LegServicesDto(LEG_ID, requests, requests.size(),
                        (int) requests.stream().filter(r -> "CONFIRMED".equals(r.status())).count(),
                        "PENDING"));
        when(crewAssignmentService.findByLeg(eq(TENANT), eq(LEG_ID), any()))
                .thenReturn(new LegCrewDto(LEG_ID, List.of(), 2, 2, true, "OK", "VALID"));
    }

    private static OccEventDto event(OccTimelineDto timeline, String key) {
        return timeline.events().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("the ten steps of the annexe are all there, in its order")
    void tenStepsInOrder() {
        Leg leg = pastLeg();
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(timeline.events()).extracting(OccEventDto::key).containsExactly(
                "creation", "dispatch", "fuel", "crew", "slot", "ground", "refuel",
                "turnaround", "closed", "nextflight");
    }

    @Test
    @DisplayName("a window that has passed with nothing recorded is late, not completed")
    void unreleasedPastWindowIsLate() {
        Leg leg = pastLeg();
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        // C'est LA difference avec l'annexe : chez elle cette ligne dirait
        // « Completed » parce que l'heure est passee.
        assertThat(event(timeline, "dispatch").status()).isEqualTo("DELAYED");
        assertThat(event(timeline, "dispatch").action()).isEqualTo("CONFIRM_RELEASE");
        assertThat(timeline.overallDelayed()).isTrue();
    }

    @Test
    @DisplayName("a signed release completes the step and takes its button away")
    void signedReleaseCompletes() {
        Leg leg = pastLeg();
        ReleaseDto release = new ReleaseDto(UUID.randomUUID(), LEG_ID, 1, null,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(7), false, null, null, null, false);
        stub(leg, Optional.of(release), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "dispatch").status()).isEqualTo("COMPLETED");
        assertThat(event(timeline, "dispatch").action()).isNull();
        assertThat(event(timeline, "dispatch").note())
                .contains("awaiting the commander's acknowledgement");
    }

    @Test
    @DisplayName("a refused fuel request drags refueling down with it")
    void refusedFuelBlocksRefuelling() {
        Leg leg = pastLeg();
        ReleaseDto release = new ReleaseDto(UUID.randomUUID(), LEG_ID, 1, null,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(7), false, null, null, null, false);
        ServiceRequestDto refused = new ServiceRequestDto(UUID.randomUUID(), LEG_ID, "DTTA",
                "FUEL", "Shell Aviation", "REFUSED", null, null, null, null, null);
        stub(leg, Optional.of(release), List.of(refused));

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "fuel").status()).isEqualTo("DELAYED");
        assertThat(event(timeline, "refuel").status()).isEqualTo("DELAYED");
    }

    @Test
    @DisplayName("a CTOT on file completes the slot step and names its reference")
    void slotOnFile() {
        Leg leg = pastLeg();
        leg.setCtot(leg.getStd().plusMinutes(20));
        leg.setCtotRef("CFMU-4471");
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "slot").status()).isEqualTo("COMPLETED");
        assertThat(event(timeline, "slot").note()).contains("CFMU-4471");
    }

    @Test
    @DisplayName("a cancelled leg cancels every step and offers no button")
    void cancelledLeg() {
        Leg leg = pastLeg();
        leg.setStatus(LegStatus.CANCELLED);
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(timeline.events()).allSatisfy(step -> {
            assertThat(step.status()).isEqualTo("CANCELLED");
            assertThat(step.action()).isNull();
        });
        assertThat(timeline.overallDelayed()).isFalse();
    }

    @Test
    @DisplayName("an incomplete crew says so, and says how many seats are missing")
    void incompleteCrew() {
        Leg leg = pastLeg();
        when(legRepository.findOneWithDetails(eq(TENANT), eq(LEG_ID))).thenReturn(Optional.of(leg));
        when(legRepository.findRotation(eq(TENANT), any(), any(), any())).thenReturn(List.of(leg));
        when(legEventRepository.findByTenantIdAndLegIdOrderByCreatedAtDesc(eq(TENANT), eq(LEG_ID)))
                .thenReturn(List.of());
        when(releaseService.findCurrent(eq(TENANT), eq(LEG_ID))).thenReturn(Optional.empty());
        when(groundServiceService.findByLeg(eq(TENANT), eq(LEG_ID)))
                .thenReturn(new LegServicesDto(LEG_ID, List.of(), 0, 0, "PENDING"));
        when(crewAssignmentService.findByLeg(eq(TENANT), eq(LEG_ID), any()))
                .thenReturn(new LegCrewDto(LEG_ID, List.of(), 1, 2, false, "UNKNOWN", "UNKNOWN"));

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "crew").note()).contains("1 of 2 seats assigned");
    }

    @Test
    @DisplayName("an FTL breach on the crew is a blocking step, not a remark")
    void ftlBreachBlocks() {
        Leg leg = pastLeg();
        when(legRepository.findOneWithDetails(eq(TENANT), eq(LEG_ID))).thenReturn(Optional.of(leg));
        when(legRepository.findRotation(eq(TENANT), any(), any(), any())).thenReturn(List.of(leg));
        when(legEventRepository.findByTenantIdAndLegIdOrderByCreatedAtDesc(eq(TENANT), eq(LEG_ID)))
                .thenReturn(List.of());
        when(releaseService.findCurrent(eq(TENANT), eq(LEG_ID))).thenReturn(Optional.empty());
        when(groundServiceService.findByLeg(eq(TENANT), eq(LEG_ID)))
                .thenReturn(new LegServicesDto(LEG_ID, List.of(), 0, 0, "PENDING"));
        when(crewAssignmentService.findByLeg(eq(TENANT), eq(LEG_ID), any()))
                .thenReturn(new LegCrewDto(LEG_ID, List.of(), 2, 2, true, "BREACH", "VALID"));

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "crew").status()).isEqualTo("DELAYED");
        assertThat(event(timeline, "crew").note()).contains("ORO.FTL");
    }

    @Test
    @DisplayName("the only rotation of the day says so instead of inventing a turnaround")
    void soleRotation() {
        Leg leg = pastLeg();
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "turnaround").status()).isEqualTo("COMPLETED");
        assertThat(event(timeline, "turnaround").note()).contains("First rotation of the day");
        assertThat(event(timeline, "nextflight").note()).contains("Last rotation of the day");
    }

    @Test
    @DisplayName("a tight turnaround after the previous sector is flagged with the figure")
    void tightTurnaround() {
        Leg leg = pastLeg();

        Aircraft aircraft = new Aircraft();
        aircraft.setRegistration("TS-NPT");
        Leg previous = new Leg();
        previous.setId(UUID.randomUUID());
        previous.setTenantId(TENANT);
        previous.setAircraft(aircraft);
        previous.setFlightNo("TNP809");
        previous.setDepIcao("LFPG");
        previous.setArrIcao("DTTA");
        previous.setStd(leg.getStd().minusHours(3));
        // Arrivee vingt minutes avant le depart : sous le minimum de cinquante.
        previous.setSta(leg.getStd().minusMinutes(20));
        previous.setStatus(LegStatus.ARRIVED);

        when(legRepository.findOneWithDetails(eq(TENANT), eq(LEG_ID))).thenReturn(Optional.of(leg));
        when(legRepository.findRotation(eq(TENANT), any(), any(), any()))
                .thenReturn(List.of(previous, leg));
        when(legEventRepository.findByTenantIdAndLegIdOrderByCreatedAtDesc(eq(TENANT), eq(LEG_ID)))
                .thenReturn(List.of());
        when(releaseService.findCurrent(eq(TENANT), eq(LEG_ID))).thenReturn(Optional.empty());
        when(groundServiceService.findByLeg(eq(TENANT), eq(LEG_ID)))
                .thenReturn(new LegServicesDto(LEG_ID, List.of(), 0, 0, "PENDING"));
        when(crewAssignmentService.findByLeg(eq(TENANT), eq(LEG_ID), any()))
                .thenReturn(new LegCrewDto(LEG_ID, List.of(), 2, 2, true, "OK", "VALID"));

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "turnaround").status()).isEqualTo("DELAYED");
        assertThat(event(timeline, "turnaround").note())
                .contains("Only 20 min ground time")
                .contains("TNP809");
    }

    @Test
    @DisplayName("a leg off blocks is taken to have been released — the annexe's own rule")
    void departedLegIsTakenAsReleased() {
        Leg leg = pastLeg();
        leg.setStatus(LegStatus.DEPARTED);
        leg.setOutAt(leg.getStd());
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "dispatch").status()).isEqualTo("COMPLETED");
        assertThat(event(timeline, "dispatch").action()).isNull();
        assertThat(event(timeline, "slot").action()).isNull();
    }

    @Test
    @DisplayName("a leg landed without its MVT is not closed, and says why")
    void awaitingMvtBeforeCloseOut() {
        Leg leg = pastLeg();
        leg.setStatus(LegStatus.ARRIVED);
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(event(timeline, "closed").note()).contains("Awaiting the MVT arrival message");
        assertThat(event(timeline, "closed").status()).isEqualTo("DELAYED");
    }

    @Test
    @DisplayName("the delay carried on the leg is reported in minutes")
    void delayMinutesFromEtd() {
        Leg leg = pastLeg();
        leg.setEtd(leg.getStd().plusMinutes(35));
        leg.setEta(leg.getSta().plusMinutes(35));
        stub(leg, Optional.empty(), List.of());

        OccTimelineDto timeline = service.timeline(TENANT, LEG_ID);

        assertThat(timeline.delayMinutes()).isEqualTo(35);
    }
}
