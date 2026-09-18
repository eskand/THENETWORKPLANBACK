package com.thenetworkplan.networkplan.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.domain.AircraftStatus;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.service.AircraftService;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import com.thenetworkplan.networkplan.ops.domain.FlightType;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptAnomalyDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptOperationDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizationResultDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizeCommand;
import com.thenetworkplan.networkplan.optimizer.service.impl.OptimizerServiceImpl;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import com.thenetworkplan.networkplan.refdata.service.AirportService;
import com.thenetworkplan.networkplan.tripsupport.service.GroundServiceService;
import com.thenetworkplan.networkplan.tripsupport.service.PermitService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le Timeline Optimizer sur un plan construit a la main.
 *
 * <p>Ce qui est teste est ce que l'annexe ne pouvait pas promettre avec un
 * moteur tire au sort : la meme fenetre rend la meme proposition deux fois,
 * un ferry devient une instruction applicable, un AOG avec des secteurs
 * planifies devient une demande de recuperation, le perimetre exclut
 * vraiment, et une fenetre horaire inversee est refusee.
 */
class OptimizerRulesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    private final LegRepository legRepository = mock(LegRepository.class);
    private final AircraftRepository aircraftRepository = mock(AircraftRepository.class);
    private final AircraftService aircraftService = mock(AircraftService.class);
    private final CrewAssignmentService crewAssignmentService = mock(CrewAssignmentService.class);
    private final PermitService permitService = mock(PermitService.class);
    private final GroundServiceService groundServiceService = mock(GroundServiceService.class);
    private final AirportService airportService = mock(AirportService.class);

    private final OptimizerServiceImpl service = new OptimizerServiceImpl(legRepository, aircraftRepository,
            aircraftService, crewAssignmentService, permitService, groundServiceService, airportService, new OpsProperties());

    private final List<Aircraft> fleet = new ArrayList<>();
    private final List<Leg> legs = new ArrayList<>();

    @BeforeEach
    void wire() {
        when(aircraftRepository.findFleet(eq(TENANT))).thenAnswer(inv -> new ArrayList<>(fleet));
        when(legRepository.findProgramme(eq(TENANT), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenAnswer(inv -> new ArrayList<>(legs));
        when(crewAssignmentService.findByLegIds(eq(TENANT), any(), any())).thenReturn(Map.of());
        when(permitService.summariseByLegIds(eq(TENANT), any())).thenReturn(Map.of());
        when(groundServiceService.summariseByLegIds(eq(TENANT), any())).thenReturn(Map.of());
        when(aircraftService.findOpenMelByAircraft(eq(TENANT))).thenReturn(Map.of());
        when(airportService.findAllByIcao(any())).thenReturn(Map.of(
                "DTTA", airport("DTTA", 36.851, 10.227),
                "LFPG", airport("LFPG", 49.010, 2.548),
                "LMML", airport("LMML", 35.857, 14.477),
                "LFMN", airport("LFMN", 43.665, 7.215)));
    }

    private static AirportDto airport(String icao, double lat, double lon) {
        return new AirportDto(icao, null, icao, null, null, BigDecimal.valueOf(lat), BigDecimal.valueOf(lon),
                null, null, null, null, null, null, null, null, null, null, false, null, null, null);
    }

    private Aircraft tail(String reg, String model, AircraftStatus status) {
        AircraftType type = new AircraftType();
        type.setIcaoType("F2TH");
        type.setModel(model);
        Aircraft aircraft = new Aircraft();
        aircraft.setId(UUID.randomUUID());
        aircraft.setTenantId(TENANT);
        aircraft.setRegistration(reg);
        aircraft.setAircraftType(type);
        aircraft.setStatus(status);
        aircraft.setHomeBaseIcao("DTTA");
        fleet.add(aircraft);
        return aircraft;
    }

    private Leg leg(Aircraft aircraft, String fn, String from, String to, int stdHour, int staHour,
                    FlightType flightType, LegStatus status) {
        Leg leg = new Leg();
        leg.setId(UUID.randomUUID());
        leg.setTenantId(TENANT);
        leg.setAircraft(aircraft);
        leg.setFlightNo(fn);
        leg.setDepIcao(from);
        leg.setArrIcao(to);
        leg.setStd(DAY.atTime(stdHour, 0).atOffset(ZoneOffset.UTC));
        leg.setSta(DAY.atTime(staHour, 0).atOffset(ZoneOffset.UTC));
        leg.setFlightType(flightType);
        leg.setStatus(status);
        leg.setPaxCount(flightType == FlightType.PAX ? 4 : 0);
        legs.add(leg);
        return leg;
    }

    private OptimizeCommand command(List<String> scope, String objective) {
        return new OptimizeCommand(DAY, 1, 0.0, 24.0, scope, objective, 0.0, null);
    }

    private static List<String> allScopes() {
        return List.of("FERRY", "CREW", "DELAY", "RECOV", "ROUTE", "FLEET", "MX", "DISPATCH", "DEMAND", "CONSTR");
    }

    private static List<String> types(OptimizationResultDto result) {
        return result.anomalies().stream().map(OptAnomalyDto::type).toList();
    }

    @Test
    @DisplayName("a planned ferry is detected and becomes an applicable cancellation")
    void ferryBecomesApplicableCancellation() {
        Aircraft a = tail("TS-NPA", "Falcon 2000LX", AircraftStatus.SERVICEABLE);
        leg(a, "TNP101", "DTTA", "LFPG", 8, 11, FlightType.PAX, LegStatus.PLANNED);
        leg(a, "TNP930", "LFPG", "LMML", 13, 16, FlightType.FERRY, LegStatus.PLANNED);

        OptimizationResultDto result = service.optimize(TENANT, command(allScopes(), "COST"));

        assertThat(types(result)).contains("FERRY_UNNECESSARY");
        OptOperationDto cancel = result.operations().stream().filter(o -> "CANCEL_LEG".equals(o.op())).findFirst().orElseThrow();
        assertThat(cancel.applicable()).isTrue();
        assertThat(cancel.ferry()).isTrue();
        assertThat(cancel.brief()).startsWith("Ferry LFPG → LMML");
        assertThat(result.costSaving()).isPositive();
        assertThat(result.finalScore()).isGreaterThanOrEqualTo(result.initialScore());
    }

    @Test
    @DisplayName("the same window optimised twice gives the same proposal — no dice")
    void deterministic() {
        Aircraft a = tail("TS-NPA", "Falcon 2000LX", AircraftStatus.SERVICEABLE);
        Aircraft b = tail("TS-NPB", "Falcon 2000LX", AircraftStatus.SERVICEABLE);
        leg(a, "TNP101", "DTTA", "LFPG", 8, 11, FlightType.PAX, LegStatus.PLANNED);
        leg(a, "TNP102", "LFPG", "DTTA", 12, 15, FlightType.PAX, LegStatus.PLANNED);
        leg(a, "TNP930", "DTTA", "LMML", 17, 18, FlightType.FERRY, LegStatus.PLANNED);
        leg(b, "TNP201", "DTTA", "LFMN", 9, 11, FlightType.PAX, LegStatus.PLANNED);

        OptimizationResultDto first = service.optimize(TENANT, command(allScopes(), "SCORE"));
        OptimizationResultDto second = service.optimize(TENANT, command(allScopes(), "SCORE"));

        assertThat(second.corrected()).isEqualTo(first.corrected());
        assertThat(second.finalScore()).isEqualTo(first.finalScore());
        assertThat(second.costSaving()).isEqualTo(first.costSaving());
        assertThat(second.operations()).extracting(OptOperationDto::brief)
                .containsExactlyElementsOf(first.operations().stream().map(OptOperationDto::brief).toList());
    }

    @Test
    @DisplayName("an AOG tail with a planned sector raises a recovery on a free same-family tail")
    void aogRecovery() {
        Aircraft grounded = tail("TS-NPK", "Falcon 2000LX", AircraftStatus.AOG);
        tail("TS-NPB", "Falcon 2000LX", AircraftStatus.SERVICEABLE);
        leg(grounded, "TNP415", "DTTA", "LFPG", 8, 11, FlightType.PAX, LegStatus.PLANNED);

        OptimizationResultDto result = service.optimize(TENANT, command(allScopes(), "SCORE"));

        assertThat(types(result)).contains("AOG");
        assertThat(result.actions()).anySatisfy(x -> {
            assertThat(x.action()).isEqualTo("REASSIGN_FLEET");
            assertThat(x.aircraft()).containsExactly("TS-NPB");
        });
        assertThat(result.operations()).anySatisfy(o -> {
            assertThat(o.op()).isEqualTo("ADD_LEG");
            assertThat(o.applicable()).isFalse();
        });
    }

    @Test
    @DisplayName("the scope is real: out-of-scope problems are detected, counted, not addressed")
    void scopeExcludes() {
        Aircraft a = tail("TS-NPA", "Falcon 2000LX", AircraftStatus.SERVICEABLE);
        leg(a, "TNP930", "DTTA", "LMML", 8, 9, FlightType.FERRY, LegStatus.PLANNED);
        tail("TS-NPB", "Falcon 2000LX", AircraftStatus.SERVICEABLE); // dormant → FLEET scope

        OptimizationResultDto result = service.optimize(TENANT, command(List.of("FLEET"), "SCORE"));

        assertThat(result.skipped()).isPositive();
        assertThat(result.anomalies()).anySatisfy(a1 -> {
            assertThat(a1.type()).isEqualTo("FERRY_UNNECESSARY");
            assertThat(a1.inScope()).isFalse();
        });
        assertThat(result.actions()).noneMatch(x -> "REMOVE_FERRY".equals(x.action()));
    }

    @Test
    @DisplayName("a tail night-stopping away from base with no follow-on leg is mispositioned")
    void mispositioned() {
        Aircraft a = tail("TS-NPA", "Falcon 2000LX", AircraftStatus.SERVICEABLE);
        leg(a, "TNP101", "DTTA", "LFPG", 8, 11, FlightType.PAX, LegStatus.PLANNED);

        OptimizationResultDto result = service.optimize(TENANT, command(allScopes(), "SCORE"));

        assertThat(result.anomalies()).anySatisfy(x -> {
            assertThat(x.type()).isEqualTo("MISPOSITIONED");
            assertThat(x.note()).contains("night-stops at LFPG");
        });
        assertThat(result.followUps()).anySatisfy(f -> assertThat(f.module()).isEqualTo("Planning"));
    }

    @Test
    @DisplayName("an inverted time window is refused before anything is read")
    void invalidWindow() {
        assertThatThrownBy(() -> service.optimize(TENANT,
                new OptimizeCommand(DAY, 1, 12.0, 5.0, allScopes(), "SCORE", 0.0, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("\"To\" time");
    }

    @Test
    @DisplayName("a window with no flight says so instead of scoring an empty plan")
    void emptyWindow() {
        tail("TS-NPA", "Falcon 2000LX", AircraftStatus.SERVICEABLE);

        assertThatThrownBy(() -> service.optimize(TENANT, command(allScopes(), "COST")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No flight found");
    }
}
