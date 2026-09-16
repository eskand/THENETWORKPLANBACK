package com.thenetworkplan.networkplan.flightfollowing.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbIngestService;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbSource;
import com.thenetworkplan.networkplan.flightfollowing.dto.FollowingBoardDto;
import com.thenetworkplan.networkplan.flightfollowing.dto.PositionDto;
import com.thenetworkplan.networkplan.flightfollowing.dto.ReportPositionCommand;
import com.thenetworkplan.networkplan.flightfollowing.service.FlightFollowingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API41 — Flight Following: the live picture and the positions behind it. */
@RestController
@RequestMapping("/v1/flight-following")
public class FlightFollowingController {

    private final FlightFollowingService flightFollowingService;
    private final AdsbIngestService adsbIngestService;

    public FlightFollowingController(FlightFollowingService flightFollowingService,
                                     AdsbIngestService adsbIngestService) {
        this.flightFollowingService = flightFollowingService;
        this.adsbIngestService = adsbIngestService;
    }

    @GetMapping("/board")
    public FollowingBoardDto board(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return flightFollowingService.findBoard(TenantContext.require(), day);
    }

    @GetMapping("/legs/{legId}/track")
    public List<PositionDto> track(@PathVariable UUID legId) {
        return flightFollowingService.findTrack(TenantContext.require(), legId);
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionDto report(@Valid @RequestBody ReportPositionCommand command,
                              @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return flightFollowingService.report(TenantContext.require(), command, actorId);
    }

    /**
     * Le trafic vivant dans la boite geographique de l exploitant.
     *
     * <p>Ce sont des appareils d autres exploitants, lus chez le fournisseur
     * ADS-B et jamais stockes : ils ne sont pas notre registre operationnel.
     * La carte les dessine d une autre couleur que nos etapes, parce qu un
     * appareil dont on repond et un appareil qu on voit passer ne sont pas la
     * meme chose.
     */
    @GetMapping("/traffic")
    public List<AdsbSource.StateVector> traffic(
            @RequestParam(name = "limit", defaultValue = "1200") int limit) {
        return adsbIngestService.traffic(TenantContext.require(), Math.max(1, Math.min(limit, 4000)));
    }
}
