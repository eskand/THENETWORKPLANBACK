package com.thenetworkplan.networkplan.sales.repository;

import com.thenetworkplan.networkplan.sales.domain.Quote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    @Query("""
            select q from Quote q
            join fetch q.request r
            join fetch r.client
            where q.tenantId = :tenantId
              and q.request.id in :requestIds
            order by q.request.id, q.version desc
            """)
    List<Quote> findByRequestIds(@Param("tenantId") UUID tenantId,
                                 @Param("requestIds") Collection<UUID> requestIds);

    @Query("""
            select q from Quote q
            join fetch q.request r
            join fetch r.client
            where q.tenantId = :tenantId
              and q.id = :id
            """)
    Optional<Quote> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            select coalesce(max(q.version), 0) from Quote q
            where q.tenantId = :tenantId
              and q.reference = :reference
            """)
    int currentVersion(@Param("tenantId") UUID tenantId, @Param("reference") String reference);
}
