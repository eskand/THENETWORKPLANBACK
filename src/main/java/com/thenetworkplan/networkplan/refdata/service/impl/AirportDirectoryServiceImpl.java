package com.thenetworkplan.networkplan.refdata.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.refdata.domain.Airport;
import com.thenetworkplan.networkplan.refdata.domain.AirportNote;
import com.thenetworkplan.networkplan.refdata.domain.Runway;
import com.thenetworkplan.networkplan.refdata.dto.AirportDetailDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportDirectoryDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportFrequencyDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportServiceDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportNoteDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportRowDto;
import com.thenetworkplan.networkplan.refdata.dto.RunwayDto;
import com.thenetworkplan.networkplan.refdata.mapper.RefDataMapper;
import com.thenetworkplan.networkplan.refdata.repository.AirportFrequencyRepository;
import com.thenetworkplan.networkplan.refdata.repository.AirportNoteRepository;
import com.thenetworkplan.networkplan.refdata.repository.AirportServiceRepository;
import com.thenetworkplan.networkplan.refdata.repository.AirportRepository;
import com.thenetworkplan.networkplan.refdata.repository.RunwayRepository;
import com.thenetworkplan.networkplan.refdata.service.AirportDirectoryService;
import com.thenetworkplan.networkplan.tripsupport.dto.SupplierDto;
import com.thenetworkplan.networkplan.tripsupport.service.TripSupportBoardService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Airports Data.
 *
 * <p>One directory, as the audit asked: {@code refdata.airports} is the only
 * table of aerodromes, and runways, notes and suppliers hang off it. The
 * usage counts come from DOM1 through {@code LegService}, and the suppliers
 * from DOM2 through its board service — no module reads another's tables.
 *
 * <p>Five statements answer the list, whatever its length.
 */
@Service
@Transactional(readOnly = true)
public class AirportDirectoryServiceImpl implements AirportDirectoryService {

    private static final int USAGE_WINDOW_DAYS = 90;

    /** What a directory rail shows before anyone scrolls. */
    private static final int DEFAULT_LIMIT = 200;

    /** The ceiling a caller cannot argue past, whatever it asks for. */
    private static final int MAX_LIMIT = 1000;

    private final AirportRepository airportRepository;
    private final RunwayRepository runwayRepository;
    private final AirportNoteRepository noteRepository;
    private final TripSupportBoardService tripSupportBoardService;
    private final LegService legService;
    private final AirportFrequencyRepository frequencyRepository;
    private final AirportServiceRepository serviceRepository;
    private final RefDataMapper mapper;

    public AirportDirectoryServiceImpl(AirportRepository airportRepository,
                                       RunwayRepository runwayRepository,
                                       AirportNoteRepository noteRepository,
                                       TripSupportBoardService tripSupportBoardService,
                                       LegService legService,
                                       AirportFrequencyRepository frequencyRepository,
                                       AirportServiceRepository serviceRepository,
                                       RefDataMapper mapper) {
        this.airportRepository = airportRepository;
        this.runwayRepository = runwayRepository;
        this.noteRepository = noteRepository;
        this.tripSupportBoardService = tripSupportBoardService;
        this.legService = legService;
        this.frequencyRepository = frequencyRepository;
        this.serviceRepository = serviceRepository;
        this.mapper = mapper;
    }

    /**
     * Search the directory, bounded.
     *
     * <p><b>Nothing here reads the whole table.</b> The cap goes into SQL, and
     * {@code usedOnly} is resolved before the select rather than after it: the
     * stations an operator actually flies to are a couple of dozen ICAO codes,
     * and asking the database for nine thousand rows in order to keep
     * twenty-six of them is the shape of the bug this replaces.
     */
    @Override
    public AirportDirectoryDto search(UUID tenantId, String search, String country,
                                      Short region, boolean usedOnly, int limit) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase() + "%";
        String iso = (country == null || country.isBlank()) ? null : country.trim().toUpperCase();
        int rowCap = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));

        Map<String, Map<String, Long>> usage = legService.countStationUsage(
                tenantId, today.minusDays(USAGE_WINDOW_DAYS));
        Map<String, Long> departures = usage.getOrDefault("DEP", Map.of());
        Map<String, Long> arrivals = usage.getOrDefault("ARR", Map.of());

        List<Airport> airports;
        long matched;
        if (usedOnly) {
            /* « Seulement les escales ou l on va » se resout avant le select :
               ops.legs connait la liste, elle tient en quelques dizaines de
               codes, et une requete par identifiant vaut mieux qu un scan. */
            Set<String> stations = new LinkedHashSet<>(departures.keySet());
            stations.addAll(arrivals.keySet());
            if (stations.isEmpty()) {
                return AirportDirectoryDto.empty();
            }
            airports = airportRepository.findByIcaoIn(stations).stream()
                    .filter(airport -> matches(airport, pattern, iso, region))
                    .sorted(Comparator.comparing(Airport::getIcao))
                    .toList();
            matched = airports.size();
            if (airports.size() > rowCap) {
                airports = airports.subList(0, rowCap);
            }
        } else {
            matched = airportRepository.countMatching(pattern, iso, region);
            airports = airportRepository.search(pattern, iso, region,
                    PageRequest.of(0, rowCap));
        }

        if (airports.isEmpty()) {
            return new AirportDirectoryDto(List.of(), matched, false);
        }
        List<UUID> ids = airports.stream().map(Airport::getId).toList();

        Map<UUID, Integer> runwayCount = new HashMap<>();
        Map<UUID, Integer> longest = new HashMap<>();
        for (Runway runway : runwayRepository.findByAirportIds(ids)) {
            runwayCount.merge(runway.getAirport().getId(), 1, Integer::sum);
            longest.merge(runway.getAirport().getId(), runway.getLengthFt(), Math::max);
        }

        Map<UUID, Integer> notesInForce = new HashMap<>();
        Map<UUID, String> worstSeverity = new HashMap<>();
        for (AirportNote note : noteRepository.findByAirportIds(ids)) {
            if (!note.isInForce(today)) {
                continue;
            }
            notesInForce.merge(note.getAirport().getId(), 1, Integer::sum);
            worstSeverity.merge(note.getAirport().getId(), note.getSeverity(),
                    (left, right) -> severity(right) > severity(left) ? right : left);
        }

        Map<String, Integer> suppliersByStation = new HashMap<>();
        for (SupplierDto supplier : tripSupportBoardService.findSuppliers(tenantId, null)) {
            suppliersByStation.merge(supplier.stationIcao(), 1, Integer::sum);
        }

        List<AirportRowDto> rows = new ArrayList<>(airports.size());
        for (Airport airport : airports) {
            long legs = departures.getOrDefault(airport.getIcao(), 0L)
                    + arrivals.getOrDefault(airport.getIcao(), 0L);
            rows.add(new AirportRowDto(
                    mapper.toDto(airport),
                    runwayCount.getOrDefault(airport.getId(), 0),
                    longest.getOrDefault(airport.getId(), 0),
                    notesInForce.getOrDefault(airport.getId(), 0),
                    worstSeverity.getOrDefault(airport.getId(), "NONE"),
                    suppliersByStation.getOrDefault(airport.getIcao(), 0),
                    legs));
        }

        // The stations the operator actually uses come first: that ordering is a
        // fact from ops.legs, not a favourite someone maintains by hand.
        rows.sort(Comparator.comparingLong(AirportRowDto::legsLast90Days).reversed()
                .thenComparing(row -> row.airport().icao()));
        return new AirportDirectoryDto(rows, matched, matched > rows.size());
    }

    /** The same predicate as the SQL, for the branch that reads by ICAO. */
    private static boolean matches(Airport airport, String pattern, String iso, Short region) {
        if (iso != null && !iso.equals(airport.getCountryIso2())) {
            return false;
        }
        if (region != null && !region.equals(airport.getRegion())) {
            return false;
        }
        if (pattern == null) {
            return true;
        }
        String needle = pattern.substring(1, pattern.length() - 1);
        return contains(airport.getIcao(), needle)
                || contains(airport.getIata(), needle)
                || contains(airport.getName(), needle)
                || contains(airport.getCity(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    @Override
    public AirportDetailDto findDetail(UUID tenantId, String icao) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String code = icao.trim().toUpperCase();
        Airport airport = airportRepository.findByIcao(code)
                .orElseThrow(() -> ResourceNotFoundException.of("Airport", code));

        List<RunwayDto> runways = runwayRepository.findByAirportIds(List.of(airport.getId())).stream()
                .map(runway -> new RunwayDto(
                        runway.getId(), runway.getDesignator(), runway.getLengthFt(),
                        runway.getWidthFt(), runway.getSurface(), runway.getLdaFt(),
                        runway.getTodaFt(), runway.getIlsCategory(), runway.getLighting()))
                .toList();

        List<AirportNoteDto> notes = noteRepository.findByAirportIds(List.of(airport.getId())).stream()
                .map(note -> new AirportNoteDto(
                        note.getId(), note.getKind(), note.getTitle(), note.getDetail(),
                        note.getValidFrom(), note.getValidTo(), note.getSeverity(),
                        note.isInForce(today)))
                .toList();

        List<SupplierDto> suppliers = tripSupportBoardService.findSuppliers(tenantId, code);

        List<AirportFrequencyDto> frequencies = frequencyRepository
                .findByAirportIdOrderByServiceAscSortOrderAsc(airport.getId()).stream()
                .map(entry -> new AirportFrequencyDto(entry.getService(), entry.getMhz()))
                .toList();

        List<AirportServiceDto> services = serviceRepository
                .findByAirportIdOrderByServiceTypeAscNameAsc(airport.getId()).stream()
                .map(entry -> new AirportServiceDto(
                        entry.getServiceType(), entry.getName(), entry.getPhone(),
                        entry.getAfterHours(), entry.getFax(), entry.getEmail(),
                        entry.getWebsite(), entry.getHours(), entry.getFuelBrands(),
                        entry.getFrequency()))
                .toList();

        Map<String, Map<String, Long>> usage = legService.countStationUsage(
                tenantId, today.minusDays(USAGE_WINDOW_DAYS));
        long departures = usage.getOrDefault("DEP", Map.of()).getOrDefault(code, 0L);
        long arrivals = usage.getOrDefault("ARR", Map.of()).getOrDefault(code, 0L);

        return new AirportDetailDto(
                mapper.toDto(airport), runways, notes, suppliers, frequencies, services,
                departures + arrivals, departures, arrivals);
    }

    private static int severity(String value) {
        return switch (value) {
            case "CRITICAL" -> 3;
            case "ATTENTION" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }
}
