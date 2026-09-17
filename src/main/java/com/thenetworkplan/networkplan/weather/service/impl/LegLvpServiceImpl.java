package com.thenetworkplan.networkplan.weather.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.weather.dto.LegLvpDto;
import com.thenetworkplan.networkplan.weather.dto.StationLvpDto;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.ObservationDto;
import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.StationWeatherDto;
import com.thenetworkplan.networkplan.weather.service.LegLvpService;
import com.thenetworkplan.networkplan.weather.service.WeatherService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le verdict de faible visibilite des deux bouts d'une etape.
 *
 * <p><b>Les seuils sont ceux de la reglementation, pas des minima
 * d'exploitant.</b> C'est la distinction qui rend ce service defendable sans la
 * table de minima que nous n'avons pas : SPA.LVO.100 DEFINIT le decollage par
 * faible visibilite a moins de 550 m de RVR, l'approbation specifique a moins de
 * 400 m, et la protection LVP a moins de 125 m. Ces trois paliers ne dependent
 * d'aucun aerodrome et d'aucun exploitant — ils disent qu'on entre dans le
 * domaine LVO, pas qu'on est au-dessus ou en dessous d'un minimum approuve.
 *
 * <p>Dire si l'etape est au-dessus de SON minimum demanderait le minimum en
 * question. Tant qu'il n'est pas en base, le verdict plafonne a
 * {@code INSUFFICIENT_DATA} et le bandeau le dit.
 */
@Service
@Transactional(readOnly = true)
public class LegLvpServiceImpl implements LegLvpService {

    /** SPA.LVO.100 — en deca, la protection LVP doit etre en vigueur. */
    private static final int LVP_PROTECTION_M = 125;
    /** SPA.LVO.100 — en deca, une approbation specifique est requise. */
    private static final int SPECIFIC_APPROVAL_M = 400;
    /** SPA.LVO.100 — en deca, on est en decollage par faible visibilite. */
    private static final int LVTO_M = 550;
    /**
     * Au-dessus, la question ne se pose pas et le bandeau se tait.
     *
     * <p>1500 m est le seuil au-dela duquel aucune approche aux instruments
     * publiee n'est limitee par la visibilite ; en deca, l'equipage doit avoir
     * lu le minimum de la carte avant le depart.
     */
    private static final int SILENT_ABOVE_M = 1500;

    /**
     * Ce que le produit ne tranche PAS, ecrit une fois et affiche partout.
     *
     * <p>Sans cette phrase, un panneau LVP tout vert se lit comme une
     * autorisation. Les trois elements cites sont ceux que le moteur de
     * l'annexe utilise et que nous n'avons pas encore en base.
     */
    private static final String NOT_ASSESSED =
            "Not assessed: approved aerodrome minima, runway lighting facilities and the TAF "
                    + "forecast window are not in the database yet — this verdict reads the METAR "
                    + "against the SPA.LVO.100 thresholds only.";

    private final LegRepository legRepository;
    private final WeatherService weatherService;

    public LegLvpServiceImpl(LegRepository legRepository, WeatherService weatherService) {
        this.legRepository = legRepository;
        this.weatherService = weatherService;
    }

    @Override
    public LegLvpDto assess(UUID tenantId, UUID legId) {
        Leg leg = legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));

        List<StationWeatherDto> stations = weatherService
                .findStations(tenantId, List.of(leg.getDepIcao(), leg.getArrIcao()))
                .stations();

        StationWeatherDto departureStation = find(stations, leg.getDepIcao());
        StationWeatherDto destinationStation = find(stations, leg.getArrIcao());
        Verdict departure = assessStation(leg.getDepIcao(), departureStation);
        Verdict destination = assessStation(leg.getArrIcao(), destinationStation);

        // Le pire des deux bouts porte le bandeau, et c'est lui que le bouton
        // ouvre : un dispatcher ne veut pas savoir que le depart va bien quand
        // c'est la destination qui est dans le brouillard.
        Verdict worst = departure.rank() >= destination.rank() ? departure : destination;

        List<StationLvpDto> detail = List.of(
                detailOf(leg.getDepIcao(), "DEPARTURE", departure, departureStation),
                detailOf(leg.getArrIcao(), "DESTINATION", destination, destinationStation));

        return new LegLvpDto(legId, worst.severity, worst.status, worst.reason,
                worst.detail, worst.station, detail);
    }

    /**
     * Le meme verdict, sur une liste d'aerodromes.
     *
     * <p>Le premier de la liste est traite comme le depart, les suivants comme
     * des destinations : sur un appareil immobilise il n'y en a qu'un, et le
     * role affiche est celui de l'escale ou il se trouve.
     */
    @Override
    public LegLvpDto assessStations(UUID tenantId, List<String> icaoCodes) {
        List<String> wanted = icaoCodes == null ? List.of() : icaoCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            return new LegLvpDto(null, "GREY", "INSUFFICIENT_DATA",
                    "No aerodrome to assess", null, null, List.of());
        }

        List<StationWeatherDto> stations =
                weatherService.findStations(tenantId, wanted).stations();

        Verdict worst = null;
        List<StationLvpDto> detail = new ArrayList<>(wanted.size());
        for (int index = 0; index < wanted.size(); index++) {
            String icao = wanted.get(index);
            StationWeatherDto station = find(stations, icao);
            Verdict verdict = assessStation(icao, station);
            if (worst == null || verdict.rank() > worst.rank()) {
                worst = verdict;
            }
            detail.add(detailOf(icao, index == 0 ? "ON GROUND" : "DESTINATION", verdict, station));
        }

        return new LegLvpDto(null, worst.severity, worst.status, worst.reason,
                worst.detail, worst.station, List.copyOf(detail));
    }

    /**
     * Le detail d'un aerodrome, avec la mesure ET sa provenance.
     *
     * <p>{@code notAssessed} n'est jamais vide tant que la table des minima
     * approuves n'existe pas : c'est la phrase qui empeche de lire un verdict
     * vert comme une autorisation.
     */
    private StationLvpDto detailOf(String icao, String role, Verdict verdict, StationWeatherDto station) {
        ObservationDto observation = station == null ? null : station.observation();
        String assessed = verdict.detail != null ? verdict.detail
                : verdict.reason != null ? verdict.reason
                : "Visibility reported well above the low-visibility thresholds of SPA.LVO.100";

        return new StationLvpDto(
                icao,
                role,
                verdict.severity,
                verdict.status,
                station == null ? "NO_OBSERVATION" : station.state(),
                observation == null ? null : observation.visibilityM(),
                observation == null ? null : observation.ceilingFt(),
                observation != null && observation.cavok(),
                observation == null ? null : observation.observedAt(),
                observation == null ? null : observation.ageMinutes(),
                observation == null ? null : observation.rawText(),
                assessed,
                NOT_ASSESSED);
    }

    private StationWeatherDto find(List<StationWeatherDto> stations, String icao) {
        return stations.stream()
                .filter(station -> station.icao().equalsIgnoreCase(icao))
                .findFirst()
                .orElse(null);
    }

    /**
     * Un bout d'etape.
     *
     * <p>L'ordre des cas est celui de l'annexe : l'absence de donnee est traitee
     * AVANT la mesure, parce qu'une visibilite manquante n'est pas une bonne
     * visibilite — c'est l'erreur precise que l'audit a relevee sur le
     * prototype.
     */
    private Verdict assessStation(String icao, StationWeatherDto station) {
        if (station == null || station.observation() == null) {
            // La phrase exacte de l'annexe (l. 73752).
            return Verdict.grey(icao, "Insufficient data — METAR/SPECI for " + icao, null);
        }
        ObservationDto observation = station.observation();

        if ("STALE".equals(station.state())) {
            return Verdict.grey(icao,
                    "Insufficient data — METAR/SPECI for " + icao + " is out of the freshness window",
                    observation.ageMinutes() + " min old");
        }

        // CAVOK est une affirmation positive : plafond et visibilite corrects.
        // Mais « la meteo est bonne » n'est PAS « au-dessus des minima » : le
        // minimum approuve de l'aerodrome n'est pas en base, donc le verdict
        // reste INSUFFICIENT_DATA, gris. Rendre du vert ici ferait dire au
        // bandeau qu'une marge a ete verifiee alors qu'aucune table ne la porte.
        if (observation.cavok()) {
            return Verdict.grey(icao,
                    "Insufficient data — approved minima for " + icao,
                    "CAVOK — visibility and ceiling both above the LVO thresholds");
        }
        Integer visibility = observation.visibilityM();
        if (visibility == null) {
            return Verdict.grey(icao,
                    "Insufficient data — no visibility reported for " + icao, null);
        }
        if (visibility >= SILENT_ABOVE_M) {
            return Verdict.grey(icao,
                    "Insufficient data — approved minima for " + icao,
                    "VIS " + (visibility >= 9999 ? "10 km or more" : visibility + " m")
                            + " — above the LVO thresholds of SPA.LVO.100");
        }

        String measured = "VIS " + visibility + " m";
        if (visibility < LVP_PROTECTION_M) {
            return Verdict.red(icao,
                    "Insufficient data — approved minima for " + icao,
                    measured + " — below 125 m, LVP protection required (SPA.LVO.100)");
        }
        if (visibility < SPECIFIC_APPROVAL_M) {
            return Verdict.red(icao,
                    "Insufficient data — approved minima for " + icao,
                    measured + " — below 400 m, specific approval required (SPA.LVO.100)");
        }
        if (visibility < LVTO_M) {
            return Verdict.amber(icao,
                    "Insufficient data — approved minima for " + icao,
                    measured + " — low visibility take-off conditions (SPA.LVO.100)");
        }
        return Verdict.amber(icao,
                "Insufficient data — approved minima for " + icao,
                measured + " — check the published minima before departure");
    }

    /**
     * Un verdict de bout d'etape.
     *
     * <p>{@code status} reste {@code INSUFFICIENT_DATA} des que la couleur n'est
     * pas verte : la couleur dit que les conditions entrent dans le domaine LVO,
     * le statut dit que le produit ne connait pas le minimum approuve pour
     * trancher. Les deux sont vrais en meme temps, et les confondre ferait dire
     * au bandeau une chose qu'aucune table ne soutient.
     */
    private record Verdict(String severity, String status, String reason, String detail, String station) {

        /*
         * PLUS AUCUN VERT. Le vert voudrait dire « au-dessus des minima
         * approuves », et aucune table de minima n'est en base : le produit ne
         * peut pas le demontrer. Tant qu'elle n'y sera pas, le meilleur verdict
         * possible est « donnee insuffisante », gris — celui que l'annexe rend
         * elle-meme quand une donnee obligatoire lui manque (l. 73763). Le
         * constructeur reste pour le jour ou les minima arriveront.
         */

        static Verdict grey(String icao, String reason, String detail) {
            return new Verdict("GREY", "INSUFFICIENT_DATA", reason, detail, icao);
        }

        static Verdict amber(String icao, String reason, String detail) {
            return new Verdict("AMBER", "INSUFFICIENT_DATA", reason, detail, icao);
        }

        static Verdict red(String icao, String reason, String detail) {
            return new Verdict("RED", "INSUFFICIENT_DATA", reason, detail, icao);
        }

        int rank() {
            return switch (severity) {
                case "RED" -> 3;
                case "AMBER" -> 2;
                case "GREY" -> 1;
                default -> 0;
            };
        }
    }
}
