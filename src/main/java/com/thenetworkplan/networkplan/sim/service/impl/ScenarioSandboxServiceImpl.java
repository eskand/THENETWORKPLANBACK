package com.thenetworkplan.networkplan.sim.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.repository.PersonRepository;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.sim.domain.Anomaly;
import com.thenetworkplan.networkplan.sim.domain.Difficulty;
import com.thenetworkplan.networkplan.sim.domain.Injector;
import com.thenetworkplan.networkplan.sim.domain.Scenario;
import com.thenetworkplan.networkplan.sim.domain.ScenarioLeg;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.AnomalyDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.BaselineDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.DifficultyDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.GenerateScenarioCommand;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.GeneratorBoardDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.InjectionResultDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.InjectorDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.ScenarioDetailDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.ScenarioDto;
import com.thenetworkplan.networkplan.sim.dto.SimDtos.ScenarioLegDto;
import com.thenetworkplan.networkplan.sim.repository.AnomalyRepository;
import com.thenetworkplan.networkplan.sim.repository.ScenarioLegRepository;
import com.thenetworkplan.networkplan.sim.repository.ScenarioRepository;
import com.thenetworkplan.networkplan.sim.service.ScenarioSandboxService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Simulation Center.
 *
 * <p><b>The sandbox promise is structural, not procedural.</b> Generating a
 * scenario reads {@code ops.legs} once and writes only into the {@code sim}
 * schema. No table in {@code sim} references {@code ops}, {@code crew} or
 * {@code camo}, so a bug in this file cannot reach the operational plan — the
 * database would refuse it. The prototype makes the same promise with a
 * localStorage key prefix, which holds until someone mistypes the prefix.
 */
@Service
@Transactional(readOnly = true)
public class ScenarioSandboxServiceImpl implements ScenarioSandboxService {

    /** Three days: the window the Simulation Center works on by default. */
    private static final short DEFAULT_HORIZON = 3;

    private final ScenarioRepository scenarioRepository;
    private final ScenarioLegRepository legRepository;
    private final AnomalyRepository anomalyRepository;
    private final LegRepository opsLegRepository;
    private final PersonRepository personRepository;

    public ScenarioSandboxServiceImpl(ScenarioRepository scenarioRepository,
                                 ScenarioLegRepository legRepository,
                                 AnomalyRepository anomalyRepository,
                                 LegRepository opsLegRepository,
                                 PersonRepository personRepository) {
        this.scenarioRepository = scenarioRepository;
        this.legRepository = legRepository;
        this.anomalyRepository = anomalyRepository;
        this.opsLegRepository = opsLegRepository;
        this.personRepository = personRepository;
    }

    @Override
    public GeneratorBoardDto findGeneratorBoard(UUID tenantId) {
        return new GeneratorBoardDto(
                baseline(tenantId, DEFAULT_HORIZON),
                Arrays.stream(Difficulty.values())
                        .map(difficulty -> new DifficultyDto(
                                difficulty.name(), difficulty.description(), difficulty.counts(),
                                difficulty.requestedCount(), difficulty.implementedCount()))
                        .toList(),
                Arrays.stream(Injector.values())
                        .map(injector -> new InjectorDto(
                                injector.key(), injector.anomaly().name(), injector.anomaly().label(),
                                injector.anomaly().severity().name(), injector.anomaly().expectedFix(),
                                injector.isImplemented()))
                        .toList(),
                scenarioRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                        .limit(10)
                        .map(this::toDto)
                        .toList());
    }

    /**
     * Counts the plan the sandbox would copy.
     *
     * <p>Counted, never declared: "120 revenue sectors" has to be a hundred and
     * twenty sectors that exist over those days. The prototype prints a figure
     * from its own file, which is why its baseline does not move when the plan
     * does.
     */
    private BaselineDto baseline(UUID tenantId, short horizonDays) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC);
        List<Leg> legs = programme(tenantId, from, horizonDays);

        return new BaselineDto(
                (int) legs.stream().map(leg -> leg.getAircraft().getId()).distinct().count(),
                (int) legs.stream().filter(leg -> "PAX".equals(leg.getFlightType().name())).count(),
                (int) personRepository.findByTenantIdAndActiveTrueOrderByLastNameAsc(tenantId).size(),
                horizonDays,
                from,
                from.plusDays(horizonDays - 1L),
                legs.stream().mapToLong(leg ->
                        java.time.Duration.between(leg.getStd(), leg.getSta()).toMinutes()).sum());
    }

    private List<Leg> programme(UUID tenantId, LocalDate from, short horizonDays) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        return opsLegRepository.findProgramme(tenantId, start, start.plusDays(horizonDays));
    }

    /**
     * Copies the plan into a scenario and puts things wrong in the copy.
     *
     * @return the scenario, with what each injector was asked for and what it placed
     */
    @Override
    @Transactional
    public ScenarioDetailDto generate(UUID tenantId, GenerateScenarioCommand command) {
        Difficulty difficulty = parseDifficulty(command.difficulty());
        short horizonDays = command.horizonDays() == null
                ? DEFAULT_HORIZON
                : command.horizonDays().shortValue();

        Map<String, Integer> requested = new LinkedHashMap<>(
                difficulty == Difficulty.CUSTOM ? Map.of() : difficulty.counts());
        if (command.counts() != null && !command.counts().isEmpty()) {
            command.counts().forEach((key, count) -> {
                if (Injector.byKey(key).isEmpty()) {
                    throw new BusinessRuleException("UNKNOWN_INJECTOR",
                            key + " is not an anomaly injector this build knows");
                }
                if (count != null && count > 0) {
                    requested.put(key, count);
                }
            });
        }
        if (requested.isEmpty()) {
            throw new BusinessRuleException("NOTHING_TO_INJECT",
                    "A scenario with no anomaly is the plan; choose a preset or set a count");
        }

        LocalDate from = LocalDate.now(ZoneOffset.UTC);
        List<Leg> plan = programme(tenantId, from, horizonDays);
        if (plan.isEmpty()) {
            throw new BusinessRuleException("EMPTY_HORIZON",
                    "No flight is planned over the next " + horizonDays
                            + " days; there is nothing to copy into a sandbox");
        }

        Scenario scenario = new Scenario();
        scenario.setTenantId(tenantId);
        scenario.setReference("SIM-" + from.getYear() + "-"
                + String.format("%04d", scenarioRepository.countByTenantId(tenantId) + 1));
        scenario.setName(command.name() == null || command.name().isBlank()
                ? difficulty.name().charAt(0) + difficulty.name().substring(1).toLowerCase()
                        + " scenario — " + from
                : command.name().trim());
        scenario.setDifficulty(difficulty);
        scenario.setHorizonFrom(from);
        scenario.setHorizonDays(horizonDays);
        // A seed the caller can hand back to reproduce this exact scenario.
        scenario.setRandomSeed(command.seed() == null
                ? System.nanoTime() : command.seed());
        scenario.setStatus("GENERATED");
        scenario.setRequested(requested);
        scenario.getSource().setType("simulation");
        scenario.getSource().setReference("Simulation Center");
        scenarioRepository.save(scenario);

        // The copy. From here on nothing reads ops.legs again.
        List<ScenarioLeg> legs = new ArrayList<>(plan.size());
        for (Leg leg : plan) {
            ScenarioLeg copy = new ScenarioLeg();
            copy.setTenantId(tenantId);
            copy.setScenario(scenario);
            copy.setSourceLegId(leg.getId());
            copy.setRegistration(leg.getAircraft().getRegistration());
            copy.setIcaoType(leg.getAircraft().getAircraftType() == null
                    ? null : leg.getAircraft().getAircraftType().getIcaoType());
            copy.setFlightNo(leg.getFlightNo());
            copy.setDepIcao(leg.getDepIcao());
            copy.setArrIcao(leg.getArrIcao());
            copy.setStd(leg.getStd());
            copy.setSta(leg.getSta());
            copy.setFlightType(leg.getFlightType().name());
            copy.setPaxCount(leg.getPaxCount());
            copy.setStatus(leg.getStatus().name());
            legs.add(copy);
        }
        legRepository.saveAll(legs);

        ScenarioGeneratorImpl generator =
                new ScenarioGeneratorImpl(scenario, legs, scenario.getRandomSeed());
        Map<String, Integer> applied = generator.run(requested);

        // Saved twice on purpose: once so every copied leg has an identifier the
        // injectors can point at, once more for the legs they created.
        legRepository.saveAll(generator.legs());
        anomalyRepository.saveAll(generator.anomalies());

        scenario.setApplied(applied);
        scenario.setBaseline(Map.of(
                "aircraft", legs.stream().map(ScenarioLeg::getRegistration).distinct().count(),
                "sectors", plan.size(),
                "blockMinutes", plan.stream().mapToLong(leg ->
                        java.time.Duration.between(leg.getStd(), leg.getSta()).toMinutes()).sum()));
        scenarioRepository.save(scenario);

        return findScenario(tenantId, scenario.getId());
    }

    @Override
    public ScenarioDetailDto findScenario(UUID tenantId, UUID scenarioId) {
        Scenario scenario = scenarioRepository.findByTenantIdAndId(tenantId, scenarioId)
                .orElseThrow(() -> ResourceNotFoundException.of("Scenario", scenarioId));

        return new ScenarioDetailDto(
                toDto(scenario),
                legRepository.findByScenario(scenarioId).stream().map(this::toDto).toList(),
                anomalyRepository.findByScenario(scenarioId).stream().map(this::toDto).toList());
    }

    @Override
    public List<ScenarioDto> findScenarios(UUID tenantId) {
        return scenarioRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Deletes a scenario and everything it holds.
     *
     * <p>Safe by construction: the cascade runs entirely inside {@code sim}.
     * "Reset simulation" on this screen cannot remove an operational leg,
     * whatever it is pointed at.
     */
    @Override
    @Transactional
    public void delete(UUID tenantId, UUID scenarioId) {
        Scenario scenario = scenarioRepository.findByTenantIdAndId(tenantId, scenarioId)
                .orElseThrow(() -> ResourceNotFoundException.of("Scenario", scenarioId));
        scenarioRepository.delete(scenario);
    }

    /* ---------- mapping ---------- */

    private ScenarioDto toDto(Scenario scenario) {
        List<InjectionResultDto> injection = scenario.getRequested().entrySet().stream()
                .map(entry -> {
                    Injector injector = Injector.byKey(entry.getKey()).orElse(null);
                    return new InjectionResultDto(
                            entry.getKey(),
                            injector == null ? entry.getKey() : injector.anomaly().label(),
                            entry.getValue(),
                            scenario.getApplied().getOrDefault(entry.getKey(), 0),
                            injector != null && injector.isImplemented());
                })
                .toList();

        List<ScenarioLeg> legs = legRepository.findByScenario(scenario.getId());

        return new ScenarioDto(
                scenario.getId(), scenario.getReference(), scenario.getName(),
                scenario.getDifficulty().name(), scenario.getHorizonFrom(), scenario.getHorizonDays(),
                scenario.getRandomSeed(), scenario.getStatus(), scenario.getCreatedAt(),
                new BaselineDto(
                        (int) legs.stream().map(ScenarioLeg::getRegistration).distinct().count(),
                        (int) legs.stream().filter(leg -> "PAX".equals(leg.getFlightType())).count(),
                        0, scenario.getHorizonDays(), scenario.getHorizonFrom(),
                        scenario.getHorizonFrom().plusDays(scenario.getHorizonDays() - 1L),
                        legs.stream().mapToLong(ScenarioLeg::blockMinutes).sum()),
                injection,
                anomalyRepository.findByScenario(scenario.getId()).size(),
                legs.size(),
                (int) legs.stream().filter(ScenarioLeg::isInjected).count());
    }

    private ScenarioLegDto toDto(ScenarioLeg leg) {
        return new ScenarioLegDto(leg.getId(), leg.getRegistration(), leg.getIcaoType(),
                leg.getFlightNo(), leg.getDepIcao(), leg.getArrIcao(), leg.getStd(), leg.getSta(),
                leg.getFlightType(), leg.getPaxCount(), leg.getStatus(), leg.getDelayMinutes(),
                leg.isInjected());
    }

    private AnomalyDto toDto(Anomaly anomaly) {
        return new AnomalyDto(anomaly.getId(), anomaly.getReference(),
                anomaly.getAnomalyType().name(), anomaly.getAnomalyType().label(),
                anomaly.getSeverity(), anomaly.getExpectedFix(), anomaly.getRegistration(),
                anomaly.getDayOffset(), anomaly.getNote(),
                anomaly.getLegIds() == null ? List.of() : List.of(anomaly.getLegIds()));
    }

    private Difficulty parseDifficulty(String name) {
        if (name == null || name.isBlank()) {
            return Difficulty.MEDIUM;
        }
        try {
            return Difficulty.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("UNKNOWN_DIFFICULTY",
                    name + " is not a difficulty preset");
        }
    }
}
