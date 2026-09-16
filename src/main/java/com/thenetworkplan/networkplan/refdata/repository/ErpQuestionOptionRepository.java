package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.ErpQuestionOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpQuestionOptionRepository
        extends JpaRepository<ErpQuestionOption, ErpQuestionOption.Key> {

    List<ErpQuestionOption> findAllByOrderBySortOrder();
}
