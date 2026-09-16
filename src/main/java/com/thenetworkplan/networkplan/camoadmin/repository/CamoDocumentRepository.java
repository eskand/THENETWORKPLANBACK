package com.thenetworkplan.networkplan.camoadmin.repository;

import com.thenetworkplan.networkplan.camoadmin.domain.CamoDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CamoDocumentRepository extends JpaRepository<CamoDocument, UUID> {

    /**
     * The library, with the documents in force first.
     *
     * <p>A superseded ARC from two years ago has the earliest expiry date of
     * anything on file, so ordering by date alone puts the obsolete
     * certificates at the top of the screen and buries the one in force.
     */
    @Query("""
            select d from CamoDocument d
            left join fetch d.aircraft a
            where d.tenantId = :tenantId
              and (:category is null or d.category = :category)
            order by case when d.status = 'CURRENT' then 0 else 1 end,
                     case when d.expiryDate is null then 1 else 0 end, d.expiryDate, d.title
            """)
    List<CamoDocument> findLibrary(@Param("tenantId") UUID tenantId,
                                   @Param("category") String category);
}
