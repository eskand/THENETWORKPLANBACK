package com.thenetworkplan.networkplan.ops.repository;

import com.thenetworkplan.networkplan.ops.domain.DelayCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DelayCodeRepository extends JpaRepository<DelayCode, UUID> {

    List<DelayCode> findByTenantIdAndActiveTrueOrderByCodeAsc(UUID tenantId);

    Optional<DelayCode> findByTenantIdAndCode(UUID tenantId, String code);
}
