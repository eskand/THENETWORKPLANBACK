package com.thenetworkplan.networkplan.ops.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un document depose dans le dossier de vol d'une etape.
 *
 * <p>Le contenu est charge en differe ({@code FetchType.LAZY}) : la liste de
 * l'onglet TRIP FOLDER lit cinq lignes de metadonnees, et ramener cinq fichiers
 * pour afficher cinq pastilles serait payer le telechargement de tout le dossier
 * a chaque ouverture.
 */
@Entity
@Table(name = "leg_documents", schema = "ops")
@Getter
@Setter
public class LegDocument extends BaseEntity {

    @Column(name = "leg_id", nullable = false)
    private UUID legId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private LegDocumentKind kind;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "uploaded_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "remark")
    private String remark;
}
