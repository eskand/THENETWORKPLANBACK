package com.thenetworkplan.networkplan.sales.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One charge on a quote.
 *
 * <p>{@code currency} and {@code fxRate} travel together and are frozen when
 * the line is written: a quote sent in March keeps March's rate, whatever the
 * market does afterwards. That is what makes a total reproducible.
 */
@Entity
@Table(name = "quote_lines", schema = "sales")
@Getter
@Setter
public class QuoteLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private QuoteLineKind kind;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit")
    private String unit;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false)
    private String currency;

    /** Rate towards the quote currency, frozen at the moment of writing. */
    @Column(name = "fx_rate", nullable = false)
    private BigDecimal fxRate = BigDecimal.ONE;

    @Column(name = "fx_rate_at")
    private LocalDate fxRateAt;

    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;
}
