package com.thenetworkplan.networkplan.sales.service;

import com.thenetworkplan.networkplan.sales.domain.QuoteLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Totals a quote.
 *
 * <p>A rule with no repository and no I/O, like {@code CrewDocumentChecker}.
 * The audit's finding was that the prototype added currencies together; this
 * calculator refuses to: every line is converted with the rate stored on it,
 * and a line with a foreign currency and a rate of one is reported as a
 * warning rather than silently summed.
 */
public interface PricingCalculator {

    Totals total(String quoteCurrency, List<QuoteLine> lines);

    /**
     * @param net           sum of the converted lines, in the quote currency
     * @param deductions    the part of {@code net} that comes from discounts
     * @param byKind        converted amount per line kind
     * @param currenciesUsed every currency appearing on the lines
     * @param warnings      lines whose conversion cannot be trusted, named
     */
    record Totals(BigDecimal net,
                  BigDecimal deductions,
                  Map<String, BigDecimal> byKind,
                  List<String> currenciesUsed,
                  List<String> warnings) {
    }
}
