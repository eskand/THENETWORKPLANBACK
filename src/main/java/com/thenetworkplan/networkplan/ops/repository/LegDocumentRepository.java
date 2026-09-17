package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.LegDocument;
import com.thenetworkplan.networkplan.ops.domain.LegDocumentKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegDocumentRepository extends JpaRepository<LegDocument, UUID> {

    List<LegDocument> findByTenantIdAndLegIdOrderByKindAsc(UUID tenantId, UUID legId);

    Optional<LegDocument> findByTenantIdAndLegIdAndKind(UUID tenantId, UUID legId, LegDocumentKind kind);

    Optional<LegDocument> findByTenantIdAndId(UUID tenantId, UUID id);
}
