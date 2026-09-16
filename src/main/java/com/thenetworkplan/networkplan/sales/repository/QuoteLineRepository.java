package com.thenetworkplan.networkplan.sales.repository;

import com.thenetworkplan.networkplan.sales.domain.QuoteLine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteLineRepository extends JpaRepository<QuoteLine, UUID> {

    /**
     * Every line of a set of quotes in one statement.
     *
     * <p>The board totals each quote, so it needs the lines of all of them:
     * one query, then one pass through the calculator per quote.
     */
    @Query("""
            select l from QuoteLine l
            where l.tenantId = :tenantId
              and l.quote.id in :quoteIds
            order by l.quote.id, l.lineNo
            """)
    List<QuoteLine> findByQuoteIds(@Param("tenantId") UUID tenantId,
                                   @Param("quoteIds") Collection<UUID> quoteIds);

    @Query("""
            select coalesce(max(l.lineNo), 0) from QuoteLine l
            where l.quote.id = :quoteId
            """)
    int lastLineNo(@Param("quoteId") UUID quoteId);
}
