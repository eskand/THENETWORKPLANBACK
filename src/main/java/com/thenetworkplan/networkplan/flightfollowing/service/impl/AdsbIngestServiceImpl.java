package com.thenetworkplan.networkplan.flightfollowing.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.config.AdsbProperties;
import com.thenetworkplan.networkplan.flightfollowing.domain.PositionProvider;
import com.thenetworkplan.networkplan.flightfollowing.domain.PositionReport;
import com.thenetworkplan.networkplan.flightfollowing.repository.PositionReportRepository;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbIngestService;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbSource;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * See {@link AdsbIngestService}. The single writer of automatic positions.
 *
 * <p>Three rules hold the whole class together.
 *
 * <p><b>Correlate on the Mode-S address, or not at all.</b> A callsign is not
 * an identity: two operators can file the same one and a crew can mistype it.
 * Proximity to a planned route is worse — it is a guess wearing the clothes of
 * a match. An aircraft with no Mode-S code on file is named in the result
 * rather than counted as a silent miss.
 *
 * <p><b>Never write the same message twice.</b> A board refreshed every
 * fifteen seconds against a feed that moves every ten would otherwise fill the
 * table with duplicates. A vector whose reported time is not newer than the
 * last stored one is dropped.
 *
 * <p><b>Attach to a leg only when the aircraft is actually flying it.</b>
 * Otherwise the position is stored against the aircraft with a null leg, which
 * is a true statement — we know where the aircraft is, we do not know which
 * sector it belongs to.
 */
@Service
public class AdsbIngestServiceImpl implements AdsbIngestService {

    private static final Logger log = LoggerFactory.getLogger(AdsbIngestServiceImpl.class);

    private final AircraftRepository aircraftRepository;
    private final LegRepository legRepository;
    private final PositionReportRepository positionRepository;
    private final List<AdsbSource> sources;
    private final AdsbProperties properties;

    /** Last result per tenant, so a read can report without refetching. */
    private final Map<UUID, Result> lastRuns = new ConcurrentHashMap<>();

    /**
     * The vectors of the last fetch, kept in memory and not in the database.
     *
     * <p>Other operators' aircraft are not our operational record: storing
     * them would grow ops.position_reports by thousands of rows a minute for
     * data we neither own nor need to keep. They live as long as the next
     * fetch, which is what a traffic picture is.
     */
    private final Map<UUID, List<AdsbSource.StateVector>> lastTraffic = new ConcurrentHashMap<>();

    public AdsbIngestServiceImpl(AircraftRepository aircraftRepository,
                                 LegRepository legRepository,
                                 PositionReportRepository positionRepository,
                                 List<AdsbSource> sources,
                                 AdsbProperties properties) {
        this.aircraftRepository = aircraftRepository;
        this.legRepository = legRepository;
        this.positionRepository = positionRepository;
        this.sources = sources;
        this.properties = properties;
    }

    @Override
    @Transactional
    public Result ingest(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdsbSource source = activeSource();

        List<Aircraft> fleet = aircraftRepository.findFleet(tenantId);
        List<String> withoutModeS = fleet.stream()
                .filter(aircraft -> aircraft.getModeSHex() == null || aircraft.getModeSHex().isBlank())
                .map(Aircraft::getRegistration)
                .sorted()
                .toList();

        if (source == null) {
            return remember(tenantId, new Result(
                    "NO_SOURCE", properties.getProvider(), 0, 0, 0, withoutModeS, now));
        }

        Result previous = lastRuns.get(tenantId);
        if (previous != null
                && Duration.between(previous.ranAt(), now).compareTo(properties.getRefreshAfter()) < 0) {
            // Asked again inside the refresh window: the feed has not moved
            // enough to be worth a request, and the allowance is finite.
            return previous;
        }

        Map<String, Aircraft> byModeS = new HashMap<>();
        for (Aircraft aircraft : fleet) {
            if (aircraft.getModeSHex() != null && !aircraft.getModeSHex().isBlank()) {
                byModeS.put(aircraft.getModeSHex().trim().toLowerCase(), aircraft);
            }
        }

        List<AdsbSource.StateVector> vectors = source.fetch();
        lastTraffic.put(tenantId, vectors);
        if (vectors.isEmpty()) {
            return remember(tenantId, new Result(
                    "NO_ANSWER", source.provider(), 0, 0, 0, withoutModeS, now));
        }

        int matched = 0;
        int stored = 0;
        List<Leg> openLegs = null;

        for (AdsbSource.StateVector vector : vectors) {
            Aircraft aircraft = byModeS.get(vector.modeSHex());
            if (aircraft == null) {
                continue;
            }
            matched++;

            if (openLegs == null) {
                openLegs = legRepository.findProgramme(
                        tenantId, now.minusHours(18), now.plusHours(6));
            }
            UUID legId = legInProgress(openLegs, aircraft.getId(), vector.reportedAt());

            if (!isNewer(tenantId, aircraft.getId(), vector.reportedAt())) {
                continue;
            }

            PositionReport report = new PositionReport();
            report.setAircraft(aircraft);
            report.setLegId(legId);
            report.setReportedAt(vector.reportedAt());
            report.setReceivedAt(now);
            report.setLatitude(BigDecimal.valueOf(vector.latitude()).setScale(6, RoundingMode.HALF_UP));
            report.setLongitude(BigDecimal.valueOf(vector.longitude()).setScale(6, RoundingMode.HALF_UP));
            report.setAltitudeFt(vector.altitudeFt());
            report.setGroundSpeedKt(vector.groundSpeedKt());
            report.setTrackDeg(vector.trackDeg());
            report.setVerticalRateFpm(vector.verticalRateFpm());
            report.setOnGround(vector.onGround());
            report.setProvider(PositionProvider.ADSB);
            report.setProviderRef(source.provider() + " " + vector.modeSHex()
                    + (vector.callsign() == null ? "" : " " + vector.callsign()));
            report.getSource().setType("integration");
            report.getSource().setReference(source.provider());
            positionRepository.save(report);
            stored++;
        }

        log.debug("ADS-B ingest: {} vectors seen, {} matched, {} stored", vectors.size(), matched, stored);
        return remember(tenantId, new Result(
                "LIVE", source.provider(), vectors.size(), matched, stored, withoutModeS, now));
    }

    @Override
    public Result lastRun(UUID tenantId) {
        Result result = lastRuns.get(tenantId);
        if (result != null) {
            return result;
        }
        return new Result("NOT_RUN", properties.getProvider(), 0, 0, 0,
                List.of(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public List<AdsbSource.StateVector> traffic(UUID tenantId, int limit) {
        List<AdsbSource.StateVector> vectors = lastTraffic.get(tenantId);
        if (vectors == null || vectors.isEmpty()) {
            return List.of();
        }
        return vectors.size() <= limit ? List.copyOf(vectors) : List.copyOf(vectors.subList(0, limit));
    }

    // ----------------------------------------------------------------

    /**
     * The leg this aircraft is flying at that instant, or null.
     *
     * <p>Only a leg that has actually departed and not yet arrived can own a
     * position. Attaching one to a leg still on the ground would put a track
     * on a sector that has not begun.
     */
    private UUID legInProgress(List<Leg> legs, UUID aircraftId, OffsetDateTime at) {
        for (Leg leg : legs) {
            if (!leg.getAircraft().getId().equals(aircraftId)) {
                continue;
            }
            if (leg.getStatus() != LegStatus.DEPARTED) {
                continue;
            }
            OffsetDateTime from = leg.getOutAt() != null ? leg.getOutAt() : leg.getStd();
            if (from != null && !at.isBefore(from)) {
                return leg.getId();
            }
        }
        return null;
    }

    /** True when this message says something the stored history does not. */
    private boolean isNewer(UUID tenantId, UUID aircraftId, OffsetDateTime reportedAt) {
        return positionRepository.findLatestForAircraft(tenantId, aircraftId)
                .map(latest -> reportedAt.isAfter(latest.getReportedAt()))
                .orElse(true);
    }

    private AdsbSource activeSource() {
        return sources.stream().filter(AdsbSource::isEnabled).findFirst().orElse(null);
    }

    private Result remember(UUID tenantId, Result result) {
        lastRuns.put(tenantId, result);
        return result;
    }
}
