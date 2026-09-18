package com.thenetworkplan.networkplan.vigil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thenetworkplan.networkplan.airworthiness.dto.AircraftDto;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.config.VigilProperties;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.tripsupport.dto.LegPermitsSummary;
import com.thenetworkplan.networkplan.tripsupport.dto.LegServicesSummary;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import com.thenetworkplan.networkplan.vigil.domain.VigilAlert;
import com.thenetworkplan.networkplan.vigil.domain.VigilAlertStatus;
import com.thenetworkplan.networkplan.vigil.domain.VigilSeverity;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilAlertDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilPanelDto;
import com.thenetworkplan.networkplan.vigil.repository.VigilAlertRepository;
import com.thenetworkplan.networkplan.vigil.service.impl.VigilServiceImpl;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Les regles VIGIL sur des faits enregistres.
 *
 * <p>Ce qui est teste est ce que l'annexe promettait et ne pouvait pas tenir
 * sans serveur : une alerte a une identite, elle est mise a jour et non
 * dupliquee a chaque balayage, elle se resout toute seule quand la condition
 * disparait, et sa gravite monte a mesure que le depart approche.
 */
class VigilRulesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AIRCRAFT = UUID.randomUUID();

    private final LegService legService = mock(LegService.class);
    private final AircraftService aircraftService = mock(AircraftService.class);
    private final CrewAssignmentService crewAssignmentService = mock(CrewAssignmentService.class);
    private final PermitService permitService = mock(PermitService.class);
    private final GroundServiceService groundServiceService = mock(GroundServiceService.class);
    private final VigilAlertRepository alertRepository = mock(VigilAlertRepository.class);

    private final VigilServiceImpl service = new VigilServiceImpl(legService, aircraftService,
            crewAssignmentService, permitService, groundServiceService, alertRepository,
            new VigilProperties(), new OpsProperties());

    private final List<VigilAlert> stored = new ArrayList<>();

    @BeforeEach
    void wireRepository() {
        when(alertRepository.findByTenantId(eq(TENANT))).thenAnswer(inv -> new ArrayList<>(stored));
        when(alertRepository.saveAll(any())).thenAnswer(inv -> {
            List<VigilAlert> saved = new ArrayList<>();
            for (VigilAlert alert : (Iterable<VigilAlert>) inv.getArgument(0)) {
                if (alert.getId() == null) {
                    alert.setId(UUID.randomUUID());
                    alert.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                }
                if (!stored.contains(alert)) {
                    stored.add(alert);
                }
                saved.add(alert);
            }
            return saved;
        });
        when(alertRepository.findActive(eq(TENANT))).thenAnswer(inv -> stored.stream()
                .filter(alert -> alert.getStatus().active()).toList());
        when(alertRepository.findByTenantIdAndId(eq(TENANT), any())).thenAnswer(inv ->
                stored.stream().filter(alert -> alert.getId().equals(inv.getArgument(1))).findFirst());
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aircraftService.findOpenMelByAircraft(eq(TENANT))).thenReturn(Map.of());
        when(aircraftService.findFleet(eq(TENANT))).thenReturn(List.of(
                new AircraftDto(AIRCRAFT, "TS-NPT", "F2TH", "Falcon 2000LX", 10, 5000, "DTTA", "DTTA",
                        "SERVICEABLE", null, null, null, null, null, null)));
    }

    private LegDto leg(String flightNo, OffsetDateTime std, String aircraftStatus, String status) {
        return new LegDto(UUID.randomUUID(), null, null, flightNo, AIRCRAFT, "TS-NPT", "F2TH",
                "Falcon 2000LX", aircraftStatus, "DTTA", "LFPG", "DTTA", std, std.plusHours(2),
                null, null, null, null, null, null, null, status, "PAX", "NON_SCHEDULED", "N", 4,
                null, null, null, "TEST-" + flightNo);
    }

    private void programme(List<LegDto> legs, Map<UUID, LegPermitsSummary> permits,
                           Map<UUID, LegServicesSummary> services, Map<UUID, LegCrewDto> crew) {
        when(legService.findProgramme(eq(TENANT), any(LocalDate.class))).thenReturn(legs);
        when(permitService.summariseByLegIds(eq(TENANT), any())).thenReturn(permits);
        when(groundServiceService.summariseByLegIds(eq(TENANT), any())).thenReturn(services);
        when(crewAssignmentService.findByLegIds(eq(TENANT), any(), any())).thenReturn(crew);
    }

    private static Optional<VigilAlertDto> alert(VigilPanelDto panel, String rule) {
        return panel.alerts().stream().filter(alert -> alert.rule().equals(rule)).findFirst();
    }

    @Test
    @DisplayName("an AOG tail with a departure inside three hours is a critical alert")
    void aogWithinThreeHours() {
        LegDto leg = leg("TNP810", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), "AOG", "PLANNED");
        programme(List.of(leg), Map.of(), Map.of(), Map.of());

        VigilPanelDto panel = service.panel(TENANT);

        assertThat(alert(panel, "AOG_FLIGHT")).isPresent();
        assertThat(alert(panel, "AOG_FLIGHT").get().severity()).isEqualTo("CRITICAL");
        assertThat(panel.state()).isEqualTo("CRITICAL");
        assertThat(panel.counts().critical()).isEqualTo(1);
    }

    @Test
    @DisplayName("a pending permit climbs from warning to high to critical as STD approaches")
    void permitSeverityLadder() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LegDto far = leg("TNP101", now.plusHours(12), "SERVICEABLE", "PLANNED");
        LegDto mid = leg("TNP102", now.plusHours(5), "SERVICEABLE", "PLANNED");
        LegDto near = leg("TNP103", now.plusHours(1), "SERVICEABLE", "PLANNED");
        programme(List.of(far, mid, near),
                Map.of(far.id(), new LegPermitsSummary(far.id(), 1, 1),
                        mid.id(), new LegPermitsSummary(mid.id(), 1, 1),
                        near.id(), new LegPermitsSummary(near.id(), 1, 1)),
                Map.of(), Map.of());

        VigilPanelDto panel = service.panel(TENANT);

        assertThat(panel.alerts().stream().filter(a -> a.rule().equals("PERMIT_PENDING"))
                .map(a -> a.flightNo() + ":" + a.severity()))
                .containsExactlyInAnyOrder("TNP101:WARNING", "TNP102:HIGH", "TNP103:CRITICAL");
    }

    @Test
    @DisplayName("the same condition on the same flight is one alert, updated, not two")
    void sameConditionIsOneAlert() {
        LegDto leg = leg("TNP810", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), "AOG", "PLANNED");
        programme(List.of(leg), Map.of(), Map.of(), Map.of());

        service.panel(TENANT);
        VigilPanelDto second = service.panel(TENANT);

        assertThat(second.alerts()).hasSize(1);
        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("an alert whose condition is no longer observed resolves itself, and says so")
    void autoResolution() {
        LegDto leg = leg("TNP810", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), "AOG", "PLANNED");
        programme(List.of(leg), Map.of(), Map.of(), Map.of());
        service.panel(TENANT);

        LegDto released = leg("TNP810", leg.std(), "SERVICEABLE", "PLANNED");
        programme(List.of(released), Map.of(), Map.of(), Map.of());
        VigilPanelDto panel = service.panel(TENANT);

        assertThat(panel.alerts()).isEmpty();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStatus()).isEqualTo(VigilAlertStatus.RESOLVED);
        assertThat(stored.get(0).getResolvedBy()).contains("condition no longer observed");
    }

    @Test
    @DisplayName("acknowledging an alert keeps it active; dismissing removes it from the panel")
    void lifecycle() {
        LegDto leg = leg("TNP810", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), "AOG", "PLANNED");
        programme(List.of(leg), Map.of(), Map.of(), Map.of());
        VigilPanelDto panel = service.panel(TENANT);
        UUID id = panel.alerts().get(0).id();

        VigilAlertDto acknowledged = service.setStatus(TENANT, id, "ACKNOWLEDGED", null);
        assertThat(acknowledged.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(service.panel(TENANT).alerts()).hasSize(1);

        service.setStatus(TENANT, id, "DISMISSED", null);
        assertThat(service.panel(TENANT).alerts()).isEmpty();
    }

    @Test
    @DisplayName("an incomplete crew names the seats missing; a complete legal crew is silent")
    void crewIncomplete() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LegDto short1 = leg("TNP201", now.plusHours(4), "SERVICEABLE", "PLANNED");
        // Neuf heures plus tard : assez d'escale pour que seule la regle equipage parle.
        LegDto full = leg("TNP202", now.plusHours(9), "SERVICEABLE", "PLANNED");
        programme(List.of(short1, full), Map.of(), Map.of(),
                Map.of(short1.id(), new LegCrewDto(short1.id(), List.of(), 1, 2, false, "UNKNOWN", "UNKNOWN"),
                        full.id(), new LegCrewDto(full.id(), List.of(), 2, 2, true, "OK", "VALID")));

        VigilPanelDto panel = service.panel(TENANT);

        assertThat(panel.alerts()).extracting(VigilAlertDto::flightNo).containsExactly("TNP201");
        assertThat(panel.alerts().get(0).why()).contains("Crew 1/2 assigned");
        assertThat(panel.alerts().get(0).severity()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("a turnaround under the operator minimum is flagged with the figure")
    void tightTurnaround() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LegDto first = leg("TNP301", now.plusHours(1), "SERVICEABLE", "PLANNED");
        // STA du premier = +3h ; le second part a +3h20 : vingt minutes d'escale.
        LegDto second = leg("TNP302", now.plusHours(3).plusMinutes(20), "SERVICEABLE", "PLANNED");
        programme(List.of(first, second), Map.of(), Map.of(), Map.of());

        VigilPanelDto panel = service.panel(TENANT);

        assertThat(alert(panel, "TURNAROUND")).isPresent();
        assertThat(alert(panel, "TURNAROUND").get().why()).contains("0h20").contains("minimum 0h50");
        assertThat(alert(panel, "TURNAROUND").get().severity()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("a flight past its ETA still PLANNED is a data-quality warning")
    void overdueStatus() {
        LegDto stale = leg("TNP401", OffsetDateTime.now(ZoneOffset.UTC).minusHours(5), "SERVICEABLE", "PLANNED");
        programme(List.of(stale), Map.of(), Map.of(), Map.of());

        VigilPanelDto panel = service.panel(TENANT);

        assertThat(alert(panel, "OVERDUE_STATUS")).isPresent();
        assertThat(alert(panel, "OVERDUE_STATUS").get().severity()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("a cancelled leg raises nothing, whatever the tail's state")
    void cancelledIsSilent() {
        LegDto leg = leg("TNP810", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), "AOG", "CANCELLED");
        programme(List.of(leg), Map.of(), Map.of(), Map.of());

        VigilPanelDto panel = service.panel(TENANT);

        assertThat(panel.alerts()).isEmpty();
        assertThat(panel.state()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Ask VIGIL answers a flight from the scan, FACT / PREDICTION / RECOMMENDATION")
    void askFlight() {
        LegDto leg = leg("TNP810", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), "AOG", "PLANNED");
        programme(List.of(leg), Map.of(), Map.of(), Map.of());

        String answer = service.ask(TENANT, "analyse TNP810").answer();

        assertThat(answer).startsWith("VIGIL ANALYSIS — TNP810")
                .contains("RISK: 66/100 — HIGH")
                .contains("FACTS:")
                .contains("is AOG")
                .contains("PREDICTION: no delay model is trained")
                .contains("RECOMMENDATION:");
    }

    @Test
    @DisplayName("Ask VIGIL says plainly what is not on file instead of inventing it")
    void askHonestGaps() {
        programme(List.of(), Map.of(), Map.of(), Map.of());

        assertThat(service.ask(TENANT, "Montre le dernier rapport").answer()).contains("not ported");
        assertThat(service.ask(TENANT, "Compare le dernier shift handover").answer()).contains("no shift handover note");
        assertThat(service.ask(TENANT, "analyse TNP999").answer()).contains("not found");
    }

    @Test
    @DisplayName("an unknown status is refused, not silently ignored")
    void unknownStatus() {
        LegDto leg = leg("TNP810", OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), "AOG", "PLANNED");
        programme(List.of(leg), Map.of(), Map.of(), Map.of());
        UUID id = service.panel(TENANT).alerts().get(0).id();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.setStatus(TENANT, id, "SNOOZED", null))
                .hasMessageContaining("Unknown VIGIL alert status");
        assertThat(stored.get(0).getSeverity()).isEqualTo(VigilSeverity.CRITICAL);
    }
}
