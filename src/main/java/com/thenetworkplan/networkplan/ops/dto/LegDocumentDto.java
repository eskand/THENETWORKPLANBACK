package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Un document du dossier de vol — ses metadonnees, jamais son contenu. */
public record LegDocumentDto(
        UUID id,
        UUID legId,
        String kind,
        String fileName,
        String contentType,
        long sizeBytes,
        OffsetDateTime uploadedAt,
        String remark) implements Serializable {
}
