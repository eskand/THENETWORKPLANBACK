package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The crisis log. Newest first: under pressure nobody scrolls to the bottom. */
public interface ErpLogEntryRepository extends JpaRepository<ErpLogEntry, UUID> {

    List<ErpLogEntry> findByTenantIdOrderByAtDesc(UUID tenantId);

    List<ErpLogEntry> findByActivationIdOrderByAtDesc(UUID activationId);
}
