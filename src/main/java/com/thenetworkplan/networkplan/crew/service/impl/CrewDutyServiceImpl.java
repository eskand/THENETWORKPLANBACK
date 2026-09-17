package com.thenetworkplan.networkplan.crew.service.impl;

import com.thenetworkplan.networkplan.crew.domain.DutyKind;
import com.thenetworkplan.networkplan.crew.domain.DutyPeriod;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.CrewDutyDto;
import com.thenetworkplan.networkplan.crew.dto.FtlExceedanceDto;
import com.thenetworkplan.networkplan.crew.repository.DutyPeriodRepository;
import com.thenetworkplan.networkplan.crew.service.CrewDutyService;
import com.thenetworkplan.networkplan.crew.service.FdpTable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duty periods across the crew, in one query.
 *
 * <p>The repository already returns them ordered by person and report time, so
 * the rest between two duties is a single pass: no query goes inside the loop,
 * whatever the size of the crew or the length of the period.
 */
@Service
@Transactional(readOnly = true)
public class CrewDutyServiceImpl implements CrewDutyService {

    private final DutyPeriodRepository dutyPeriodRepository;

    public CrewDutyServiceImpl(DutyPeriodRepository dutyPeriodRepository) {
        this.dutyPeriodRepository = dutyPeriodRepository;
    }

    @Override
    public List<CrewDutyDto> findDuties(UUID tenantId, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<UUID, OffsetDateTime> previousOffDuty = new HashMap<>();
        List<CrewDutyDto> out = new ArrayList<>();

        for (DutyPeriod duty : dutyPeriodRepository.findInWindow(tenantId, start, end)) {
            Person person = duty.getPerson();
            boolean counts = duty.getKind().countsAsDuty();

            /* Le repos se mesure d une garde a la suivante. Un repos est lui
               aussi enregistre comme periode : le chainer comme les autres
               couperait chaque repos en deux et ferait apparaitre une infraction
               a chaque fois qu un equipage se repose effectivement. */
            OffsetDateTime previous = counts
                    ? previousOffDuty.put(person.getId(), duty.getOffDutyAt())
                    : previousOffDuty.get(person.getId());

            /* Le repos ne se mesure que quand on a vu la garde precedente. La
               premiere garde de la fenetre en a une, ailleurs : la compter
               comme un long repos serait exactement le tick vert sur une
               donnee absente que l'audit reproche. */
            Long restBefore = !counts || previous == null || previous.isAfter(duty.getReportAt())
                    ? null
                    : ChronoUnit.MINUTES.between(previous, duty.getReportAt());

            long dutyMinutes = ChronoUnit.MINUTES.between(duty.getReportAt(), duty.getOffDutyAt());

            out.add(new CrewDutyDto(
                    duty.getId(),
                    person.getId(),
                    person.getStaffNo(),
                    person.getFirstName() + " " + person.getLastName(),
                    person.getMainRole().name(),
                    person.getBaseIcao(),
                    duty.getLegId(),
                    duty.getKind().name(),
                    duty.getKind().rosterCode(),
                    duty.getReportAt(),
                    duty.getOffDutyAt(),
                    dutyMinutes,
                    duty.getBlockMinutes(),
                    duty.getSectors(),
                    duty.getRemark(),
                    restBefore,
                    maxFdp(duty),
                    counts));
        }
        return out;
    }

    /**
     * Every FTL exceedance in the window, worst day first.
     *
     * <p>Rest and days off are filtered out before anything is counted. They
     * are stored as duty periods — that is how a roster records them — and a
     * rolling 28-day total that summed eighteen-hour rest periods would put the
     * whole crew over the 190-hour ceiling and bury the one duty that really
     * breached it.
     */
    @Override
    public List<FtlExceedanceDto> findExceedances(UUID tenantId, LocalDate from, LocalDate to) {
        List<CrewDutyDto> duties = findDuties(tenantId, from, to).stream()
                .filter(CrewDutyDto::countsAsDuty)
                .toList();

        List<FtlExceedanceDto> out = new ArrayList<>();

        for (CrewDutyDto duty : duties) {
            if (duty.maxFdpMinutes() != null && duty.dutyMinutes() > duty.maxFdpMinutes()) {
                out.add(new FtlExceedanceDto(
                        duty.reportAt().toLocalDate(), duty.personId(), duty.fullName(),
                        "ORO.FTL.205 — max daily FDP",
                        "FDP " + hhmm(duty.dutyMinutes()) + " against a maximum of "
                                + hhmm(duty.maxFdpMinutes()) + " for "
                                + Math.max(1, duty.sectors()) + " sector(s)",
                        duty.dutyMinutes() - duty.maxFdpMinutes(), "High"));
            }

            /* ORO.FTL.235(a) : douze heures, ou la duree de la garde
               precedente si elle est plus longue. */
            if (duty.restBeforeMinutes() != null) {
                long floor = Math.max(12 * 60, previousDuty(duties, duty));
                if (duty.restBeforeMinutes() < floor) {
                    out.add(new FtlExceedanceDto(
                            duty.reportAt().toLocalDate(), duty.personId(), duty.fullName(),
                            "ORO.FTL.235(a) — minimum rest",
                            "Rest of " + hhmm(duty.restBeforeMinutes())
                                    + "; minimum required " + hhmm(floor),
                            floor - duty.restBeforeMinutes(), "High"));
                }
            }
        }

        out.addAll(rollingExceedances(duties));
        out.sort(Comparator.comparing(FtlExceedanceDto::day).reversed());
        return out;
    }

    /** The rolling 7 and 28-day duty ceilings of ORO.FTL.210(a), per person-day. */
    private static List<FtlExceedanceDto> rollingExceedances(List<CrewDutyDto> duties) {
        Map<UUID, Map<LocalDate, Long>> byCrew = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        duties.forEach(duty -> {
            names.putIfAbsent(duty.personId(), duty.fullName());
            byCrew.computeIfAbsent(duty.personId(), key -> new LinkedHashMap<>())
                    .merge(duty.reportAt().toLocalDate(), duty.dutyMinutes(), Long::sum);
        });

        List<FtlExceedanceDto> out = new ArrayList<>();
        byCrew.forEach((personId, days) -> days.keySet().stream().sorted().forEach(day -> {
            long week = sumBack(days, day, 6);
            long month = sumBack(days, day, 27);
            if (week > FdpTable.DUTY_CEILING_7_DAYS_MINUTES) {
                out.add(new FtlExceedanceDto(day, personId, names.get(personId),
                        "ORO.FTL.210(a)(1) — 60 h / 7 days",
                        "Rolling 7-day duty of " + hhmm(week) + " against a 60:00 ceiling",
                        week - FdpTable.DUTY_CEILING_7_DAYS_MINUTES, "Medium"));
            }
            if (month > FdpTable.DUTY_CEILING_28_DAYS_MINUTES) {
                out.add(new FtlExceedanceDto(day, personId, names.get(personId),
                        "ORO.FTL.210(a)(2) — 190 h / 28 days",
                        "Rolling 28-day duty of " + hhmm(month) + " against a 190:00 ceiling",
                        month - FdpTable.DUTY_CEILING_28_DAYS_MINUTES, "Medium"));
            }
        }));
        return out;
    }

    private static long sumBack(Map<LocalDate, Long> days, LocalDate day, int back) {
        long total = 0;
        for (int step = 0; step <= back; step++) {
            total += days.getOrDefault(day.minusDays(step), 0L);
        }
        return total;
    }

    /** The duty immediately before this one, for the same person, in minutes. */
    private static long previousDuty(List<CrewDutyDto> duties, CrewDutyDto duty) {
        return duties.stream()
                .filter(other -> other.personId().equals(duty.personId())
                        && other.offDutyAt().isBefore(duty.reportAt()))
                .max(Comparator.comparing(CrewDutyDto::offDutyAt))
                .map(CrewDutyDto::dutyMinutes)
                .orElse(0L);
    }

    private static String hhmm(long minutes) {
        return minutes / 60 + ":" + (Math.abs(minutes % 60) < 10 ? "0" : "") + Math.abs(minutes % 60);
    }

    /**
     * The FDP ceiling of a duty, or null when Table 2 does not speak about it.
     *
     * <p>Standby, office and training are duty but not flight duty: giving them
     * a flight-duty ceiling would make every long training day look like a
     * breach, and the real breaches would be lost in the noise.
     */
    private static Integer maxFdp(DutyPeriod duty) {
        if (duty.getKind() != DutyKind.FLIGHT_DUTY) {
            return null;
        }
        OffsetDateTime report = duty.getReportAt().withOffsetSameInstant(ZoneOffset.UTC);
        int minuteOfDay = report.getHour() * 60 + report.getMinute();
        return FdpTable.maxFdp(minuteOfDay, Math.max(1, duty.getSectors())).minutes();
    }
}
