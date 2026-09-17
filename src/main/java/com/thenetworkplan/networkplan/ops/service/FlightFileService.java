package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.dto.FlightNoteDto;
import com.thenetworkplan.networkplan.ops.dto.LegDocumentDto;
import com.thenetworkplan.networkplan.ops.dto.LegEventDto;
import com.thenetworkplan.networkplan.ops.dto.LegPassengerDto;
import com.thenetworkplan.networkplan.ops.dto.LegPaxDto;
import com.thenetworkplan.networkplan.ops.dto.SaveLegPassengerCommand;
import com.thenetworkplan.networkplan.ops.dto.TripFolderDto;
import java.util.List;
import java.util.UUID;

/**
 * Les deux onglets du dossier de vol qui portent des donnees propres a l'etape :
 * le manifeste passagers et le dossier documentaire.
 *
 * <p>Ils sont servis ensemble parce qu'ils repondent a la meme question — ce
 * que l'etape emporte — et parce que la cloture du vol les concerne tous les
 * deux : un dossier clos est en lecture seule, passagers compris.
 */
public interface FlightFileService {

    LegPaxDto passengers(UUID tenantId, UUID legId);

    LegPassengerDto addPassenger(UUID tenantId, UUID legId, SaveLegPassengerCommand command);

    LegPassengerDto updatePassenger(UUID tenantId, UUID passengerId, SaveLegPassengerCommand command);

    void deletePassenger(UUID tenantId, UUID passengerId);

    TripFolderDto tripFolder(UUID tenantId, UUID legId);

    LegDocumentDto upload(UUID tenantId, UUID legId, String kind, String fileName,
                          String contentType, byte[] content, UUID actorId);

    void deleteDocument(UUID tenantId, UUID documentId);

    /** Le contenu d'un document, pour le telechargement. */
    LoadedDocument download(UUID tenantId, UUID documentId);

    TripFolderDto saveRemark(UUID tenantId, UUID legId, String remark);

    /**
     * Le journal de l'etape, du plus recent au plus ancien — l'entree
     * « OCC Dispatch » du menu du dossier de vol.
     */
    List<LegEventDto> events(UUID tenantId, UUID legId);

    FlightNoteDto flightNote(UUID tenantId, UUID legId);

    FlightNoteDto saveFlightNote(UUID tenantId, UUID legId, String note, UUID actorId);

    /** Metadonnees plus octets — ce qu'une reponse de telechargement doit porter. */
    record LoadedDocument(String fileName, String contentType, byte[] content) {
    }
}
