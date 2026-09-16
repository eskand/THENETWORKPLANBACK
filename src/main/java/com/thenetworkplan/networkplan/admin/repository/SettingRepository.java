package com.thenetworkplan.networkplan.admin.repository;

import com.thenetworkplan.networkplan.admin.domain.Setting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, UUID> {

    List<Setting> findByTenantIdOrderByCategoryAscSettingKeyAsc(UUID tenantId);

    Optional<Setting> findByTenantIdAndSettingKey(UUID tenantId, String settingKey);

    Optional<Setting> findByTenantIdAndId(UUID tenantId, UUID id);
}
