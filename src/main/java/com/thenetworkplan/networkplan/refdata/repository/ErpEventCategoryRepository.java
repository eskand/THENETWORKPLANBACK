package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.ErpEventCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpEventCategoryRepository extends JpaRepository<ErpEventCategory, String> {

    List<ErpEventCategory> findAllByOrderBySortOrder();
}
