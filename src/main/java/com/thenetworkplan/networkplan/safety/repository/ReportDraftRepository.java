package com.thenetworkplan.networkplan.safety.repository;

import com.thenetworkplan.networkplan.safety.domain.ReportDraft;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportDraftRepository extends JpaRepository<ReportDraft, UUID> {

    /**
     * The drafts one person has not sent.
     *
     * <p>Scoped to the author at the query, not filtered afterwards: a draft
     * that leaves this repository belonging to somebody else has already leaked.
     */
    @Query("""
            select d from ReportDraft d
            where d.tenantId = :tenantId
              and d.authorId = :authorId
              and d.submittedAt is null
            order by d.updatedAt desc
            """)
    List<ReportDraft> findOpenByAuthor(@Param("tenantId") UUID tenantId,
                                       @Param("authorId") UUID authorId);

    Optional<ReportDraft> findByTenantIdAndId(UUID tenantId, UUID id);
}
