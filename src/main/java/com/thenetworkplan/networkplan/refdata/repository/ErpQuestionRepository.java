package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.ErpQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpQuestionRepository extends JpaRepository<ErpQuestion, String> {

    List<ErpQuestion> findAllByOrderBySortOrder();
}
