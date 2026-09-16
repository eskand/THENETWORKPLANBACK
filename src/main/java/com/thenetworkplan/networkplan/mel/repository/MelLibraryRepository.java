package com.thenetworkplan.networkplan.mel.repository;

import com.thenetworkplan.networkplan.mel.domain.MelLibraryItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MelLibraryRepository extends JpaRepository<MelLibraryItem, UUID> {

    @Query("""
            select m from MelLibraryItem m
            left join fetch m.aircraftType t
            where m.tenantId = :tenantId
              and (:icaoType is null or t.icaoType = :icaoType or t is null)
              and (:ataChapter is null or m.ataChapter = :ataChapter)
            order by m.ataChapter, m.itemRef
            """)
    List<MelLibraryItem> findLibrary(@Param("tenantId") UUID tenantId,
                                     @Param("icaoType") String icaoType,
                                     @Param("ataChapter") String ataChapter);

    @Query("""
            select m from MelLibraryItem m
            left join fetch m.aircraftType
            where m.tenantId = :tenantId
              and m.id = :id
            """)
    Optional<MelLibraryItem> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
