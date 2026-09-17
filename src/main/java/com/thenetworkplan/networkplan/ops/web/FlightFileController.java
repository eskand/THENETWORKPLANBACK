package com.thenetworkplan.networkplan.ops.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.ops.dto.FlightNoteDto;
import com.thenetworkplan.networkplan.ops.dto.LegDocumentDto;
import com.thenetworkplan.networkplan.ops.dto.LegEventDto;
import com.thenetworkplan.networkplan.ops.dto.SaveFlightNoteCommand;
import com.thenetworkplan.networkplan.ops.dto.LegPassengerDto;
import com.thenetworkplan.networkplan.ops.dto.LegPaxDto;
import com.thenetworkplan.networkplan.ops.dto.SaveLegPassengerCommand;
import com.thenetworkplan.networkplan.ops.dto.TripFolderDto;
import com.thenetworkplan.networkplan.ops.service.FlightFileService;
import com.thenetworkplan.networkplan.tripsupport.dto.LegFuelDto;
import com.thenetworkplan.networkplan.weather.dto.LegLvpDto;
import com.thenetworkplan.networkplan.tripsupport.service.FuelService;
import com.thenetworkplan.networkplan.weather.service.LegLvpService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Les onglets PAX, FUEL et TRIP FOLDER du dossier de vol.
 *
 * <p>Ils sont sur le meme controleur parce qu'ils sont le meme ecran : un agent
 * qui ouvre une etape les parcourt d'affilee, et les separer en trois
 * controleurs n'aurait servi qu'a suivre le decoupage des tables.
 */
@RestController
@RequestMapping("/v1")
public class FlightFileController {

    private final FlightFileService flightFileService;
    private final FuelService fuelService;
    private final LegLvpService legLvpService;

    public FlightFileController(FlightFileService flightFileService,
                                FuelService fuelService,
                                LegLvpService legLvpService) {
        this.flightFileService = flightFileService;
        this.fuelService = fuelService;
        this.legLvpService = legLvpService;
    }

    /* PAX */

    @GetMapping("/legs/{legId}/passengers")
    public LegPaxDto passengers(@PathVariable UUID legId) {
        return flightFileService.passengers(TenantContext.require(), legId);
    }

    @PostMapping("/legs/{legId}/passengers")
    @ResponseStatus(HttpStatus.CREATED)
    public LegPassengerDto addPassenger(@PathVariable UUID legId,
                                        @Valid @RequestBody SaveLegPassengerCommand command) {
        return flightFileService.addPassenger(TenantContext.require(), legId, command);
    }

    @PutMapping("/passengers/{passengerId}")
    public LegPassengerDto updatePassenger(@PathVariable UUID passengerId,
                                           @Valid @RequestBody SaveLegPassengerCommand command) {
        return flightFileService.updatePassenger(TenantContext.require(), passengerId, command);
    }

    @DeleteMapping("/passengers/{passengerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePassenger(@PathVariable UUID passengerId) {
        flightFileService.deletePassenger(TenantContext.require(), passengerId);
    }

    /**
     * L'import CSV du tableau passagers — « Import CSV / Excel » de l'annexe.
     *
     * <p>Le fichier est lu ICI et non dans le navigateur : l'annexe analysait le
     * CSV en JavaScript et n'ecrivait que dans {@code localStorage}, si bien
     * qu'un manifeste importe restait sur le poste de celui qui l'avait importe.
     */
    @PostMapping("/legs/{legId}/passengers/import")
    public List<LegPassengerDto> importPassengers(@PathVariable UUID legId,
                                                  @RequestPart("file") MultipartFile file)
            throws IOException {
        return PassengerCsv.parse(file.getBytes()).stream()
                .map(command -> flightFileService.addPassenger(TenantContext.require(), legId, command))
                .toList();
    }

    /* TRIP FOLDER */

    @GetMapping("/legs/{legId}/trip-folder")
    public TripFolderDto tripFolder(@PathVariable UUID legId) {
        return flightFileService.tripFolder(TenantContext.require(), legId);
    }

    @PutMapping("/legs/{legId}/trip-folder/remark")
    public TripFolderDto saveRemark(@PathVariable UUID legId, @RequestBody RemarkPayload payload) {
        return flightFileService.saveRemark(TenantContext.require(), legId, payload.remark());
    }

    @PostMapping("/legs/{legId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public LegDocumentDto upload(@PathVariable UUID legId,
                                 @RequestParam("kind") String kind,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId)
            throws IOException {
        return flightFileService.upload(TenantContext.require(), legId, kind,
                file.getOriginalFilename(), file.getContentType(), file.getBytes(), actorId);
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<Resource> download(@PathVariable UUID documentId) {
        FlightFileService.LoadedDocument document =
                flightFileService.download(TenantContext.require(), documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(document.contentType()))
                .body(new ByteArrayResource(document.content()));
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID documentId) {
        flightFileService.deleteDocument(TenantContext.require(), documentId);
    }

    /* FUEL */

    @GetMapping("/legs/{legId}/fuel")
    public LegFuelDto fuel(@PathVariable UUID legId) {
        return fuelService.findByLeg(TenantContext.require(), legId);
    }

    /* MENU DE L'EN-TETE (les cinq entrees du menu ⋮ de l'annexe) */

    /** « OCC Dispatch — event timeline » : le journal de l'etape. */
    @GetMapping("/legs/{legId}/events")
    public List<LegEventDto> events(@PathVariable UUID legId) {
        return flightFileService.events(TenantContext.require(), legId);
    }

    /** « Flight note » : la consigne d'exploitation portee par l'etape. */
    @GetMapping("/legs/{legId}/note")
    public FlightNoteDto flightNote(@PathVariable UUID legId) {
        return flightFileService.flightNote(TenantContext.require(), legId);
    }

    @PutMapping("/legs/{legId}/note")
    public FlightNoteDto saveFlightNote(@PathVariable UUID legId,
                                        @Valid @RequestBody SaveFlightNoteCommand command,
                                        @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return flightFileService.saveFlightNote(TenantContext.require(), legId, command.note(), actorId);
    }

    /**
     * Le bandeau de faible visibilite du haut de l'onglet FLIGHT.
     *
     * <p>GREEN veut dire : pas de bandeau. L'annexe fait pareil.
     */
    @GetMapping("/legs/{legId}/lvp")
    public LegLvpDto lvp(@PathVariable UUID legId) {
        return legLvpService.assess(TenantContext.require(), legId);
    }

    /** Le corps d'une remarque de dossier — un seul champ, nomme. */
    public record RemarkPayload(String remark) {
    }
}
