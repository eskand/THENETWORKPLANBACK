package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegEvent;
import com.thenetworkplan.networkplan.ops.domain.LegEventKind;
import com.thenetworkplan.networkplan.ops.domain.LegDocument;
import com.thenetworkplan.networkplan.ops.domain.LegDocumentKind;
import com.thenetworkplan.networkplan.ops.domain.LegPassenger;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.domain.TravelDocumentType;
import com.thenetworkplan.networkplan.ops.dto.FlightNoteDto;
import com.thenetworkplan.networkplan.ops.dto.LegDocumentDto;
import com.thenetworkplan.networkplan.ops.dto.LegEventDto;
import com.thenetworkplan.networkplan.ops.dto.LegPassengerDto;
import com.thenetworkplan.networkplan.ops.dto.LegPaxDto;
import com.thenetworkplan.networkplan.ops.dto.SaveLegPassengerCommand;
import com.thenetworkplan.networkplan.ops.dto.TripFolderDto;
import com.thenetworkplan.networkplan.ops.repository.LegDocumentRepository;
import com.thenetworkplan.networkplan.ops.repository.LegEventRepository;
import com.thenetworkplan.networkplan.ops.repository.LegPassengerRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.FlightFileService;
import com.thenetworkplan.networkplan.ops.service.LegEventRecorder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le manifeste passagers et le dossier documentaire d'une etape.
 *
 * <p><b>Un dossier clos ne s'ecrit plus.</b> L'annexe passe son bloc de
 * remarques en lecture seule apres la cloture mais laisse le reste modifiable —
 * une cloture qui n'empeche rien n'est pas une cloture. Ici toute ecriture est
 * refusee sur une etape CLOSED, et il faut la rouvrir pour corriger.
 */
@Service
@Transactional(readOnly = true)
public class FlightFileServiceImpl implements FlightFileService {

    /** Vingt-cinq mega-octets : un OFP scanne genereusement, pas une video. */
    private static final long MAX_DOCUMENT_BYTES = 25L * 1024 * 1024;

    private final LegRepository legRepository;
    private final LegPassengerRepository passengerRepository;
    private final LegDocumentRepository documentRepository;
    private final LegEventRepository eventRepository;
    private final LegEventRecorder eventRecorder;

    public FlightFileServiceImpl(LegRepository legRepository,
                                 LegPassengerRepository passengerRepository,
                                 LegDocumentRepository documentRepository,
                                 LegEventRepository eventRepository,
                                 LegEventRecorder eventRecorder) {
        this.legRepository = legRepository;
        this.passengerRepository = passengerRepository;
        this.documentRepository = documentRepository;
        this.eventRepository = eventRepository;
        this.eventRecorder = eventRecorder;
    }

    /* passagers */

    @Override
    public LegPaxDto passengers(UUID tenantId, UUID legId) {
        Leg leg = requireLeg(tenantId, legId);
        List<LegPassenger> rows = passengerRepository.findByTenantIdAndLegIdOrderBySeqAsc(tenantId, legId);
        LocalDate flightDay = leg.effectiveDeparture().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();

        List<LegPassengerDto> dtos = rows.stream().map(row -> toDto(row, flightDay)).toList();
        int checkedIn = (int) rows.stream().filter(LegPassenger::isCheckedIn).count();
        int notValid = (int) dtos.stream().filter(dto -> !dto.documentValid()).count();
        List<String> requests = rows.stream()
                .map(LegPassenger::getSpecialRequest)
                .filter(request -> request != null && !request.isBlank())
                .distinct()
                .toList();

        return new LegPaxDto(legId, leg.getPaxCount(), checkedIn, rows.size(), notValid, requests, dtos);
    }

    @Override
    @Transactional
    public LegPassengerDto addPassenger(UUID tenantId, UUID legId, SaveLegPassengerCommand command) {
        Leg leg = requireOpenLeg(tenantId, legId);
        int next = (int) passengerRepository.countByTenantIdAndLegId(tenantId, legId) + 1;

        LegPassenger passenger = new LegPassenger();
        passenger.setTenantId(tenantId);
        passenger.setLegId(legId);
        passenger.setSeq(next);
        apply(passenger, command);
        LegPassenger saved = passengerRepository.save(passenger);
        return toDto(saved, leg.effectiveDeparture().atZoneSameInstant(ZoneOffset.UTC).toLocalDate());
    }

    @Override
    @Transactional
    public LegPassengerDto updatePassenger(UUID tenantId, UUID passengerId, SaveLegPassengerCommand command) {
        LegPassenger passenger = passengerRepository.findByTenantIdAndId(tenantId, passengerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Passenger", passengerId));
        Leg leg = requireOpenLeg(tenantId, passenger.getLegId());
        apply(passenger, command);
        LegPassenger saved = passengerRepository.save(passenger);
        return toDto(saved, leg.effectiveDeparture().atZoneSameInstant(ZoneOffset.UTC).toLocalDate());
    }

    @Override
    @Transactional
    public void deletePassenger(UUID tenantId, UUID passengerId) {
        LegPassenger passenger = passengerRepository.findByTenantIdAndId(tenantId, passengerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Passenger", passengerId));
        requireOpenLeg(tenantId, passenger.getLegId());
        passengerRepository.delete(passenger);
    }

    /* dossier de vol */

    @Override
    public TripFolderDto tripFolder(UUID tenantId, UUID legId) {
        Leg leg = requireLeg(tenantId, legId);
        List<LegDocumentDto> documents = documentRepository
                .findByTenantIdAndLegIdOrderByKindAsc(tenantId, legId)
                .stream().map(this::toDto).toList();
        return new TripFolderDto(legId, leg.getFlightNo(), leg.getStatus() == LegStatus.CLOSED,
                leg.getRemark(), closureBlockers(leg), documents);
    }

    @Override
    @Transactional
    public LegDocumentDto upload(UUID tenantId, UUID legId, String kind, String fileName,
                                 String contentType, byte[] content, UUID actorId) {
        requireOpenLeg(tenantId, legId);
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("DOCUMENT_EMPTY", "An empty file is not a document");
        }
        if (content.length > MAX_DOCUMENT_BYTES) {
            throw new BusinessRuleException("DOCUMENT_TOO_LARGE",
                    "A trip folder document is capped at 25 MB");
        }
        LegDocumentKind parsed = parseKind(kind);

        // Redeposer un document REMPLACE le precedent : le dossier porte la
        // derniere version, pas une pile de brouillons dont personne ne sait
        // laquelle l'equipage a emportee.
        LegDocument document = documentRepository
                .findByTenantIdAndLegIdAndKind(tenantId, legId, parsed)
                .orElseGet(LegDocument::new);
        document.setTenantId(tenantId);
        document.setLegId(legId);
        document.setKind(parsed);
        document.setFileName(fileName == null || fileName.isBlank() ? parsed.name() : fileName);
        document.setContentType(contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType);
        document.setSizeBytes(content.length);
        document.setContent(content);
        document.setUploadedBy(actorId);
        return toDto(documentRepository.save(document));
    }

    @Override
    @Transactional
    public void deleteDocument(UUID tenantId, UUID documentId) {
        LegDocument document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", documentId));
        requireOpenLeg(tenantId, document.getLegId());
        documentRepository.delete(document);
    }

    @Override
    public LoadedDocument download(UUID tenantId, UUID documentId) {
        LegDocument document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", documentId));
        return new LoadedDocument(document.getFileName(), document.getContentType(), document.getContent());
    }

    @Override
    @Transactional
    public TripFolderDto saveRemark(UUID tenantId, UUID legId, String remark) {
        Leg leg = requireOpenLeg(tenantId, legId);
        leg.setRemark(remark);
        legRepository.save(leg);
        return tripFolder(tenantId, legId);
    }

    /* journal et note de vol */

    @Override
    public List<LegEventDto> events(UUID tenantId, UUID legId) {
        requireLeg(tenantId, legId);
        return eventRepository.findByTenantIdAndLegIdOrderByCreatedAtDesc(tenantId, legId)
                .stream().map(this::toDto).toList();
    }

    @Override
    public FlightNoteDto flightNote(UUID tenantId, UUID legId) {
        Leg leg = requireLeg(tenantId, legId);
        return new FlightNoteDto(legId, leg.getFlightNote(),
                leg.getFlightNoteAt(), leg.getFlightNoteBy());
    }

    /**
     * Ecrire la note laisse une trace au journal.
     *
     * <p>Une consigne d'exploitation change la facon dont l'etape est conduite ;
     * elle appartient donc a l'historique au meme titre qu'un changement d'heure.
     * L'annexe l'ecrivait dans un objet du navigateur et personne ne pouvait
     * ensuite dire qui avait ecrit quoi.
     */
    @Override
    @Transactional
    public FlightNoteDto saveFlightNote(UUID tenantId, UUID legId, String note, UUID actorId) {
        Leg leg = requireOpenLeg(tenantId, legId);
        String cleaned = blankToNull(note);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("flightNote", leg.getFlightNote());

        leg.setFlightNote(cleaned);
        leg.setFlightNoteAt(cleaned == null ? null : OffsetDateTime.now());
        leg.setFlightNoteBy(cleaned == null ? null : actorId);
        Leg saved = legRepository.save(leg);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("flightNote", cleaned);
        eventRecorder.record(tenantId, legId, LegEventKind.REMARK, before, after, actorId,
                cleaned == null ? "Flight note cleared" : "Flight note updated");

        return new FlightNoteDto(legId, saved.getFlightNote(),
                saved.getFlightNoteAt(), saved.getFlightNoteBy());
    }

    private LegEventDto toDto(LegEvent event) {
        return new LegEventDto(event.getId(), event.getLegId(), event.getKind().name(),
                event.getCreatedAt(), event.getActorId(), event.getReason(),
                event.getPayloadBefore(), event.getPayloadAfter());
    }

    /* aides */

    /**
     * Ce qui empeche la cloture, en toutes lettres.
     *
     * <p>La regle est celle de LegMovementService.close() : une etape ne se clot
     * pas avant que ses heures reelles soient enregistrees. Le dossier l'annonce
     * a l'avance plutot que de laisser l'agent decouvrir le refus au clic.
     */
    private List<String> closureBlockers(Leg leg) {
        List<String> blockers = new ArrayList<>(2);
        if (leg.getOutAt() == null) {
            blockers.add("actual time of departure (ATD)");
        }
        if (leg.getInAt() == null) {
            blockers.add("actual time of arrival (ATA)");
        }
        return blockers;
    }

    private void apply(LegPassenger passenger, SaveLegPassengerCommand command) {
        passenger.setSurname(command.surname().trim());
        passenger.setGivenName(blankToNull(command.givenName()));
        passenger.setDocumentType(parseDocumentType(command.documentType()));
        passenger.setDocumentNumber(blankToNull(command.documentNumber()));
        passenger.setDocumentExpiry(command.documentExpiry());
        passenger.setNationality(blankToNull(command.nationality()));
        passenger.setDateOfBirth(command.dateOfBirth());
        passenger.setCheckedIn(command.checkedIn());
        passenger.setSpecialRequest(blankToNull(command.specialRequest()));
    }

    /**
     * Un document est valide s'il porte un numero, un type et une expiration
     * posterieure au vol. L'absence d'expiration n'est pas une validite : c'est
     * une information manquante, et elle bloque l'embarquement de la meme facon.
     */
    private LegPassengerDto toDto(LegPassenger passenger, LocalDate flightDay) {
        boolean valid = passenger.getDocumentType() != null
                && passenger.getDocumentNumber() != null && !passenger.getDocumentNumber().isBlank()
                && passenger.getDocumentExpiry() != null
                && !passenger.getDocumentExpiry().isBefore(flightDay);
        return new LegPassengerDto(passenger.getId(), passenger.getLegId(), passenger.getSeq(),
                passenger.getSurname(), passenger.getGivenName(),
                passenger.getDocumentType() == null ? null : passenger.getDocumentType().name(),
                passenger.getDocumentNumber(), passenger.getDocumentExpiry(),
                passenger.getNationality(), passenger.getDateOfBirth(),
                passenger.isCheckedIn(), passenger.getSpecialRequest(), valid);
    }

    private LegDocumentDto toDto(LegDocument document) {
        return new LegDocumentDto(document.getId(), document.getLegId(), document.getKind().name(),
                document.getFileName(), document.getContentType(), document.getSizeBytes(),
                document.getUploadedAt(), document.getRemark());
    }

    private Leg requireLeg(UUID tenantId, UUID legId) {
        return legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));
    }

    private Leg requireOpenLeg(UUID tenantId, UUID legId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getStatus() == LegStatus.CLOSED) {
            throw new BusinessRuleException("LEG_CLOSED",
                    "The trip folder of a closed flight is read-only — reopen the leg to change it");
        }
        return leg;
    }

    private LegDocumentKind parseKind(String raw) {
        try {
            return LegDocumentKind.valueOf(String.valueOf(raw).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("DOCUMENT_KIND_UNKNOWN", "Unknown document kind: " + raw);
        }
    }

    private TravelDocumentType parseDocumentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TravelDocumentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("TRAVEL_DOCUMENT_UNKNOWN", "Unknown travel document: " + raw);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
