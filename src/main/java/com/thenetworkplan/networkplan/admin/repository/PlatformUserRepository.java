package com.thenetworkplan.networkplan.admin.repository;

import com.thenetworkplan.networkplan.admin.domain.PlatformUser;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    List<PlatformUser> findByTenantIdOrderByDisplayName(UUID tenantId);
}
