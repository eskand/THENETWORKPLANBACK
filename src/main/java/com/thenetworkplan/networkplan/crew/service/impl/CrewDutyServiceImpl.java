package com.thenetworkplan.networkplan.crew.service.impl;

import com.thenetworkplan.networkplan.crew.domain.DutyKind;
import com.thenetworkplan.networkplan.crew.domain.DutyPeriod;
import com.thenetworkplan.networkplan.crew.domain.Person;
import com.thenetworkplan.networkplan.crew.dto.CrewDutyDto;
import com.thenetworkplan.networkplan.crew.repository.DutyPeriodRepository;
import com.thenetworkplan.networkplan.crew.service.CrewDutyService;
import com.thenetworkplan.networkplan.crew.service.FdpTable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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
            OffsetDateTime previous = previousOffDuty.put(person.getId(), duty.getOffDutyAt());

            /* Le repos ne se mesure que quand on a vu la garde precedente. La
               premiere garde de la fenetre en a une, ailleurs : la compter
               comme un long repos serait exactement le tick vert sur une
               donnee absente que l'audit reproche. */
            Long restBefore = previous == null || previous.isAfter(duty.getReportAt())
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
                    maxFdp(duty)));
        }
        return out;
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
