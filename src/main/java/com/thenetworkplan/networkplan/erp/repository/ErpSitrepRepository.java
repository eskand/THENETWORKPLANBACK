package com.thenetworkplan.networkplan.erp.repository;

import com.thenetworkplan.networkplan.erp.domain.ErpSitrep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpSitrepRepository extends JpaRepository<ErpSitrep, UUID> {

    List<ErpSitrep> findByActivationIdOrderByAtDesc(UUID activationId);
}
