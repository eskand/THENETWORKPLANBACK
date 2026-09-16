package com.thenetworkplan.networkplan.reporting.repository;

import com.thenetworkplan.networkplan.reporting.domain.ReportDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {

    List<ReportDefinition> findByTenantIdOrderByDomainAscCodeAsc(UUID tenantId);

    Optional<ReportDefinition> findByTenantIdAndCode(UUID tenantId, String code);
}
