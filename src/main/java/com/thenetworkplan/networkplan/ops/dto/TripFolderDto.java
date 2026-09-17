package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * L'onglet TRIP FOLDER d'une etape.
 *
 * <p>{@code closureBlockers} porte ce qui empeche la cloture, en toutes lettres.
 * L'annexe ecrit la meme phrase sous son bouton (« Requires actual time of
 * departure (ATD) and actual time of arrival (ATA) ») ; la difference est que la
 * regle est ici celle du serveur, celle-la meme que POST /legs/{id}/close
 * appliquera.
 */
public record TripFolderDto(
        UUID legId,
        String flightNo,
        boolean closed,
        String remark,
        List<String> closureBlockers,
        List<LegDocumentDto> documents) implements Serializable {
}
