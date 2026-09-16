package com.thenetworkplan.networkplan.sales.repository;

import com.thenetworkplan.networkplan.sales.domain.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    Optional<Client> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Client> findByTenantIdAndCode(UUID tenantId, String code);
}
