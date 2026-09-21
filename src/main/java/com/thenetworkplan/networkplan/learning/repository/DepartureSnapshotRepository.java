package com.thenetworkplan.networkplan.learning.repository;

import com.thenetworkplan.networkplan.learning.domain.DepartureSnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartureSnapshotRepository extends JpaRepository<DepartureSnapshot, UUID> {

    boolean existsByTenantIdAndLegId(UUID tenantId, UUID legId);

    /** Les photos dont le resultat n'est pas encore connu. */
    List<DepartureSnapshot> findByTenantIdAndOutcomeAtIsNull(UUID tenantId);

    /** Les photos d'une fenetre de departs programmes, dans l'ordre des departs. */
    List<DepartureSnapshot> findByTenantIdAndStdGreaterThanEqualAndStdLessThanOrderByStdAsc(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to);
}
