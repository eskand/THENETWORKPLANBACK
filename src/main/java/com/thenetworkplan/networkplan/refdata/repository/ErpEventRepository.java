package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.ErpEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpEventRepository extends JpaRepository<ErpEvent, String> {

    List<ErpEvent> findAllByOrderBySortOrder();
}
