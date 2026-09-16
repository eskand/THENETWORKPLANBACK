package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpActivationCheck;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpActivationCheckRepository extends JpaRepository<ErpActivationCheck, UUID> {

    List<ErpActivationCheck> findByActivationId(UUID activationId);

    Optional<ErpActivationCheck> findByActivationIdAndItemCode(UUID activationId, String itemCode);
}
