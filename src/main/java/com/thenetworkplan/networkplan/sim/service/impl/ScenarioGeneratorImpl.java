package com.thenetworkplan.networkplan.sim.service.impl;

import com.thenetworkplan.networkplan.sim.domain.Anomaly;
import com.thenetworkplan.networkplan.sim.domain.AnomalyType;
import com.thenetworkplan.networkplan.sim.domain.Injector;
import com.thenetworkplan.networkplan.sim.domain.Scenario;
import com.thenetworkplan.networkplan.sim.domain.ScenarioLeg;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Puts things wrong in a copy of the plan.
 *
 * <p><b>Nothing here touches the operational plan.</b> It receives a list of
 * {@link ScenarioLeg} — already copies, already in the {@code sim} schema — and
 * mutates those. It has no repository and no transaction of its own, which is
 * the cheapest way to be sure: a class with no writer cannot write to the
 * wrong table.
 *
 * <p><b>Every injector reports what it placed.</b> Asking for ten unnecessary
 * ferries and getting six is a normal outcome — a ferry needs a ground gap long
 * enough to fly out and back, and a busy plan has fewer of those. The caller
 * records both numbers so the scenario cannot claim anomalies it does not hold.
 *
 * <p><b>The randomness is seeded.</b> Same seed, same plan, same preset, same
 * scenario — which is what lets two crews be given the same exercise.
 */
public class ScenarioGeneratorImpl {

    /** Below this, a ground gap is a turnaround, not an opportunity to waste. */
    private static final long MIN_GAP_MINUTES = 145;

    /** A plausible short out-and-back for the fleet in question. */
    private static final int FERRY_BLOCK_MINUTES = 55;

    private final Random random;
    private final List<ScenarioLeg> legs;
    private final Scenario scenario;
    private final List<Anomaly> anomalies = new ArrayList<>();
    private int anomalySeq = 0;
    private int ferrySeq = 900;

    public ScenarioGeneratorImpl(Scenario scenario, List<ScenarioLeg> legs, long seed) {
        this.scenario = scenario;
        this.legs = legs;
        this.random = new Random(seed);
    }

    public List<Anomaly> anomalies() {
        return anomalies;
    }

    public List<ScenarioLeg> legs() {
        return legs;
    }

    /**
     * Runs every requested injector and reports what each one placed.
     *
     * @param requested injector key to count
     * @return injector key to the number actually injected
     */
    public Map<String, Integer> run(Map<String, Integer> requested) {
        Map<String, Integer> applied = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            Injector injector = Injector.byKey(entry.getKey()).orElse(null);
            if (injector == null || !injector.isImplemented()) {
                applied.put(entry.getKey(), 0);
                continue;
            }
            applied.put(entry.getKey(), inject(injector, entry.getValue()));
        }
        return applied;
    }

    private int inject(Injector injector, int count) {
        return switch (injector) {
            case FERRY -> ferries(count);
            case DELAY -> delays(count);
            case CANCELLATION -> cancellations(count);
            case AOG -> aircraftOnGround(count);
            case MISPOSITIONED -> mispositioned(count);
            case ROTATION_INEFFICIENT -> inefficientRotations(count);
            case REPOSITIONING -> repositionings(count);
            case CREW_CONFLICT -> crewConflicts(count);
            case UNDER_CREW -> crewUtilisation(count, true);
            case OVER_CREW -> crewUtilisation(count, false);
            case MX_CRITICAL -> criticalMaintenance(count);
            default -> 0;
        };
    }

    /* ---------- injectors ---------- */

    /**
     * An out-and-back ferry dropped into a ground gap, going nowhere useful.
     *
     * <p>The classic waste a fleet optimiser is supposed to find: the aircraft
     * ends where it started, burnt fuel and hours, and carried nobody. It is
     * only placed where the gap is genuinely long enough, so the resulting plan
     * stays physically possible — an impossible plan teaches nothing.
     */
    private int ferries(int count) {
        int placed = 0;
        List<Gap> gaps = gaps();
        for (Gap gap : gaps) {
            if (placed >= count) {
                break;
            }
            if (gap.minutes() < MIN_GAP_MINUTES) {
                continue;
            }
            String destination = nearbyOf(gap.airport);
            if (destination == null || destination.equals(gap.airport)) {
                continue;
            }

            OffsetDateTime out = gap.from.plusMinutes(15);
            ScenarioLeg first = newLeg(gap.registration, gap.icaoType, gap.airport, destination,
                    out, out.plusMinutes(FERRY_BLOCK_MINUTES));
            OffsetDateTime back = out.plusMinutes(FERRY_BLOCK_MINUTES + 20L);
            ScenarioLeg second = newLeg(gap.registration, gap.icaoType, destination, gap.airport,
                    back, back.plusMinutes(FERRY_BLOCK_MINUTES));

            record(AnomalyType.FERRY_UNNECESSARY, gap.registration, dayOffset(out),
                    "Out-and-back ferry " + gap.airport + "–" + destination + " with no revenue purpose",
                    List.of(first, second));
            placed++;
        }
        return placed;
    }

    /**
     * A delay, with the downstream rotation pushed by the same amount.
     *
     * <p>Delaying one leg and leaving the next where it was would produce an
     * aircraft in two places, which no plan can be. The whole chain after it on
     * that tail and that day moves.
     */
    private int delays(int count) {
        int placed = 0;
        List<ScenarioLeg> candidates = shuffled(legs.stream()
                .filter(leg -> leg.getDelayMinutes() == null)
                .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                .toList());

        for (ScenarioLeg leg : candidates) {
            if (placed >= count) {
                break;
            }
            int minutes = 20 + random.nextInt(100);
            List<ScenarioLeg> chain = legs.stream()
                    .filter(other -> other.getRegistration().equals(leg.getRegistration()))
                    .filter(other -> !other.getStd().isBefore(leg.getStd()))
                    .filter(other -> !"CANCELLED".equals(other.getStatus()))
                    .sorted(Comparator.comparing(ScenarioLeg::getStd))
                    .toList();

            chain.forEach(other -> {
                other.setStd(other.getStd().plusMinutes(minutes));
                other.setSta(other.getSta().plusMinutes(minutes));
            });
            leg.setDelayMinutes(minutes);
            leg.setStatus("DELAYED");

            record(AnomalyType.DELAY, leg.getRegistration(), dayOffset(leg.getStd()),
                    minutes + " min delay — " + chain.size() + " downstream leg"
                            + (chain.size() > 1 ? "s" : "") + " shifted",
                    chain);
            placed++;
        }
        return placed;
    }

    /** A cancelled sector. The demand it carried has to go somewhere. */
    private int cancellations(int count) {
        int placed = 0;
        for (ScenarioLeg leg : shuffled(revenueLegs())) {
            if (placed >= count) {
                break;
            }
            leg.setStatus("CANCELLED");
            record(AnomalyType.CANCELLATION, leg.getRegistration(), dayOffset(leg.getStd()),
                    leg.getFlightNo() + " " + leg.getDepIcao() + "–" + leg.getArrIcao()
                            + " cancelled, " + leg.getPaxCount() + " passengers to re-accommodate",
                    List.of(leg));
            placed++;
        }
        return placed;
    }

    /**
     * An aircraft grounded for a day. Every sector it held is unflown.
     *
     * <p>The most expensive anomaly to solve, and the one that tests whether a
     * solver can reassign across the fleet rather than only within a tail.
     */
    private int aircraftOnGround(int count) {
        int placed = 0;
        for (String registration : shuffled(registrations())) {
            if (placed >= count) {
                break;
            }
            List<ScenarioLeg> affected = legs.stream()
                    .filter(leg -> leg.getRegistration().equals(registration))
                    .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                    .toList();
            if (affected.isEmpty()) {
                continue;
            }
            affected.forEach(leg -> leg.setStatus("CANCELLED"));
            record(AnomalyType.AOG, registration, dayOffset(affected.get(0).getStd()),
                    registration + " on ground — " + affected.size() + " sector"
                            + (affected.size() > 1 ? "s" : "") + " to reassign across the fleet",
                    affected);
            placed++;
        }
        return placed;
    }

    /**
     * A tail that starts the day where its first sector does not.
     *
     * <p>Injected by rewriting the departure of the first leg so the aircraft
     * is somewhere it could not have got to — a positioning flight is missing
     * and the solver has to notice.
     */
    private int mispositioned(int count) {
        int placed = 0;
        for (String registration : shuffled(registrations())) {
            if (placed >= count) {
                break;
            }
            ScenarioLeg first = legs.stream()
                    .filter(leg -> leg.getRegistration().equals(registration))
                    .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                    .min(Comparator.comparing(ScenarioLeg::getStd))
                    .orElse(null);
            if (first == null) {
                continue;
            }
            String elsewhere = nearbyOf(first.getDepIcao());
            if (elsewhere == null || elsewhere.equals(first.getArrIcao())) {
                continue;
            }
            String wasAt = first.getDepIcao();
            first.setDepIcao(elsewhere);

            record(AnomalyType.MISPOSITIONED, registration, dayOffset(first.getStd()),
                    registration + " starts at " + elsewhere + " but the sector was planned from "
                            + wasAt + " — no positioning flight covers the difference",
                    List.of(first));
            placed++;
        }
        return placed;
    }

    /**
     * A rotation that goes the long way round.
     *
     * <p>Two consecutive sectors on one tail are swapped, so the aircraft
     * crosses its own track. Nothing becomes impossible; it becomes wasteful,
     * which is the point.
     */
    private int inefficientRotations(int count) {
        int placed = 0;
        for (String registration : shuffled(registrations())) {
            if (placed >= count) {
                break;
            }
            List<ScenarioLeg> rotation = legs.stream()
                    .filter(leg -> leg.getRegistration().equals(registration))
                    .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                    .sorted(Comparator.comparing(ScenarioLeg::getStd))
                    .toList();
            if (rotation.size() < 3) {
                continue;
            }
            ScenarioLeg leg = rotation.get(1);
            String detour = nearbyOf(leg.getArrIcao());
            if (detour == null || detour.equals(leg.getDepIcao())) {
                continue;
            }
            String direct = leg.getArrIcao();
            leg.setArrIcao(detour);
            leg.setSta(leg.getSta().plusMinutes(35));

            record(AnomalyType.ROTATION_INEFFICIENT, registration, dayOffset(leg.getStd()),
                    "Routed via " + detour + " where " + leg.getDepIcao() + "–" + direct
                            + " was direct — 35 min and one extra landing",
                    List.of(leg));
            placed++;
        }
        return placed;
    }

    /** A positioning flight that no longer serves anything. */
    private int repositionings(int count) {
        int placed = 0;
        for (Gap gap : gaps()) {
            if (placed >= count) {
                break;
            }
            String destination = nearbyOf(gap.airport);
            if (destination == null || gap.minutes() < FERRY_BLOCK_MINUTES + 30L) {
                continue;
            }
            OffsetDateTime out = gap.from.plusMinutes(10);
            ScenarioLeg leg = newLeg(gap.registration, gap.icaoType, gap.airport, destination,
                    out, out.plusMinutes(FERRY_BLOCK_MINUTES));
            leg.setFlightType("POSITIONING");

            record(AnomalyType.REPOSITIONING_UNNECESSARY, gap.registration, dayOffset(out),
                    "Positioning to " + destination + " with no sector behind it",
                    List.of(leg));
            placed++;
        }
        return placed;
    }

    /**
     * A duty that cannot legally be flown.
     *
     * <p>Injected as a rotation whose first departure and last arrival span
     * more than thirteen hours — the point at which a two-pilot duty needs
     * either an extension or a different crew. The anomaly is recorded against
     * the legs; which crew member breaks is the solver's problem, and naming
     * one here would be inventing a roster this scenario does not hold.
     */
    private int crewConflicts(int count) {
        int placed = 0;
        for (String registration : shuffled(registrations())) {
            if (placed >= count) {
                break;
            }
            List<ScenarioLeg> day = legs.stream()
                    .filter(leg -> leg.getRegistration().equals(registration))
                    .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                    .sorted(Comparator.comparing(ScenarioLeg::getStd))
                    .toList();
            if (day.size() < 2) {
                continue;
            }
            ScenarioLeg last = day.get(day.size() - 1);
            long span = Duration.between(day.get(0).getStd(), last.getSta()).toMinutes();
            long needed = 13 * 60 - span;
            if (needed > 0) {
                last.setStd(last.getStd().plusMinutes(needed + 30));
                last.setSta(last.getSta().plusMinutes(needed + 30));
            }

            record(AnomalyType.CREW_CONFLICT, registration, dayOffset(day.get(0).getStd()),
                    "Flight duty period of " + ((span + Math.max(0, needed) + 30) / 60)
                            + " h on " + registration + " — beyond a two-pilot duty without extension",
                    List.of(last));
            placed++;
        }
        return placed;
    }

    /**
     * Crew utilisation pulled off balance.
     *
     * <p>Recorded as an observation on a tail's day rather than by moving legs:
     * this scenario holds no roster, and a crew imbalance the scenario cannot
     * show would be a claim rather than an anomaly. The solver is told which
     * day to look at, and the expected fix says what to do.
     */
    private int crewUtilisation(int count, boolean under) {
        int placed = 0;
        List<String> tails = shuffled(registrations());
        for (String registration : tails) {
            if (placed >= count) {
                break;
            }
            List<ScenarioLeg> day = legs.stream()
                    .filter(leg -> leg.getRegistration().equals(registration))
                    .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                    .sorted(Comparator.comparing(ScenarioLeg::getStd))
                    .toList();
            if (day.isEmpty()) {
                continue;
            }
            long block = day.stream().mapToLong(ScenarioLeg::blockMinutes).sum();

            record(under ? AnomalyType.UNDERUTILIZED_CREW : AnomalyType.OVERUTILIZED_CREW,
                    registration, dayOffset(day.get(0).getStd()),
                    (under ? "Crew on " : "Crew on ") + registration + " at "
                            + (block / 60) + " h " + (block % 60) + " min of block over the day — "
                            + (under ? "well under" : "at the top of") + " the balance the roster assumes",
                    day);
            placed++;
        }
        return placed;
    }

    /**
     * A maintenance limit that falls inside the horizon.
     *
     * <p>Recorded against the tail's last sector of the window: that is the one
     * the solver has to move or ground, and pointing at the whole day would not
     * say which decision is due.
     */
    private int criticalMaintenance(int count) {
        int placed = 0;
        for (String registration : shuffled(registrations())) {
            if (placed >= count) {
                break;
            }
            ScenarioLeg last = legs.stream()
                    .filter(leg -> leg.getRegistration().equals(registration))
                    .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                    .max(Comparator.comparing(ScenarioLeg::getSta))
                    .orElse(null);
            if (last == null) {
                continue;
            }
            record(AnomalyType.MX_CRITICAL, registration, dayOffset(last.getSta()),
                    registration + " reaches a maintenance limit after "
                            + last.getFlightNo() + " — the sector flies or the check slips",
                    List.of(last));
            placed++;
        }
        return placed;
    }

    /* ---------- helpers ---------- */

    /** A stretch on the ground between two consecutive sectors of one tail. */
    private record Gap(String registration, String icaoType, String airport,
                       OffsetDateTime from, OffsetDateTime to) {
        long minutes() {
            return Duration.between(from, to).toMinutes();
        }
    }

    /**
     * Every ground gap in the plan, longest first.
     *
     * <p>Longest first so the injectors take the roomiest opportunities and
     * succeed more often; a random order would report fewer anomalies applied
     * for no reason other than the order it happened to try.
     */
    private List<Gap> gaps() {
        List<Gap> found = new ArrayList<>();
        Map<String, List<ScenarioLeg>> byTail = legs.stream()
                .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                .collect(Collectors.groupingBy(ScenarioLeg::getRegistration));

        byTail.forEach((registration, tailLegs) -> {
            List<ScenarioLeg> sorted = tailLegs.stream()
                    .sorted(Comparator.comparing(ScenarioLeg::getStd))
                    .toList();
            for (int i = 0; i < sorted.size() - 1; i++) {
                ScenarioLeg before = sorted.get(i);
                ScenarioLeg after = sorted.get(i + 1);
                // Only a gap where the aircraft actually stays put.
                if (!before.getArrIcao().equals(after.getDepIcao())) {
                    continue;
                }
                found.add(new Gap(registration, before.getIcaoType(), before.getArrIcao(),
                        before.getSta(), after.getStd()));
            }
        });

        found.sort(Comparator.comparingLong(Gap::minutes).reversed());
        return found;
    }

    private List<ScenarioLeg> revenueLegs() {
        return legs.stream()
                .filter(leg -> "PAX".equals(leg.getFlightType()))
                .filter(leg -> !"CANCELLED".equals(leg.getStatus()))
                .toList();
    }

    private List<String> registrations() {
        return legs.stream().map(ScenarioLeg::getRegistration).distinct().toList();
    }

    /**
     * Somewhere plausible to send an aircraft from here.
     *
     * <p>Taken from the plan itself — an airport the fleet already serves —
     * rather than from a table of neighbours. A ferry to an aerodrome the
     * operator has never used would be an anomaly for the wrong reason.
     */
    private String nearbyOf(String airport) {
        List<String> served = legs.stream()
                .map(ScenarioLeg::getArrIcao)
                .distinct()
                .filter(icao -> !icao.equals(airport))
                .sorted()
                .toList();
        return served.isEmpty() ? null : served.get(random.nextInt(served.size()));
    }

    private ScenarioLeg newLeg(String registration, String icaoType, String from, String to,
                               OffsetDateTime std, OffsetDateTime sta) {
        ScenarioLeg leg = new ScenarioLeg();
        leg.setTenantId(scenario.getTenantId());
        leg.setScenario(scenario);
        leg.setRegistration(registration);
        leg.setIcaoType(icaoType);
        leg.setFlightNo("TNP" + (++ferrySeq) + "F");
        leg.setDepIcao(from);
        leg.setArrIcao(to);
        leg.setStd(std);
        leg.setSta(sta);
        leg.setFlightType("FERRY");
        leg.setPaxCount(0);
        leg.setStatus("PLANNED");
        leg.setInjected(true);
        legs.add(leg);
        return leg;
    }

    private void record(AnomalyType type, String registration, short dayOffset, String note,
                        List<ScenarioLeg> affected) {
        Anomaly anomaly = new Anomaly();
        anomaly.setTenantId(scenario.getTenantId());
        anomaly.setScenario(scenario);
        anomaly.setReference(scenario.getReference() + "-A-" + String.format("%03d", ++anomalySeq));
        anomaly.setAnomalyType(type);
        anomaly.setSeverity(type.severity().name());
        anomaly.setExpectedFix(type.expectedFix());
        anomaly.setRegistration(registration);
        anomaly.setDayOffset(dayOffset);
        anomaly.setNote(note);
        // The ids are filled in after the legs are saved: a leg created in this
        // pass has no identifier yet, and storing a null would be worse than
        // storing nothing.
        anomaly.setLegIds(affected.stream()
                .map(ScenarioLeg::getId)
                .filter(java.util.Objects::nonNull)
                .toArray(UUID[]::new));
        anomalies.add(anomaly);
    }

    private short dayOffset(OffsetDateTime at) {
        return (short) Duration.between(
                scenario.getHorizonFrom().atStartOfDay().atOffset(at.getOffset()), at).toDays();
    }

    private <T> List<T> shuffled(List<T> source) {
        List<T> copy = new ArrayList<>(source);
        java.util.Collections.shuffle(copy, random);
        return copy;
    }
}
