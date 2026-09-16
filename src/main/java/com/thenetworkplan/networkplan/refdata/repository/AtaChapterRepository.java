package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.AtaChapter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtaChapterRepository extends JpaRepository<AtaChapter, String> {

    List<AtaChapter> findAllByOrderBySortOrder();
}
