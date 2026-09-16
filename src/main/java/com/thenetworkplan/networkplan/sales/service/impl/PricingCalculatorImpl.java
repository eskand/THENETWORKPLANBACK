package com.thenetworkplan.networkplan.sales.service.impl;

import com.thenetworkplan.networkplan.sales.domain.QuoteLine;
import com.thenetworkplan.networkplan.sales.service.PricingCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Line amount = quantity × unit price × rate, rounded to the cent once, at the
 * end of each line — never on the running total, which is how rounding drifts.
 *
 * <p>No tax is computed. The operator has declared no tax rule in this
 * iteration, so the quote shows a net total and says that tax is not included,
 * rather than showing a zero that looks like an exemption.
 */
@Component
public class PricingCalculatorImpl implements PricingCalculator {

    @Override
    public Totals total(String quoteCurrency, List<QuoteLine> lines) {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        Map<String, BigDecimal> byKind = new LinkedHashMap<>();
        Set<String> currencies = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        for (QuoteLine line : lines) {
            currencies.add(line.getCurrency());

            if (!line.getCurrency().equalsIgnoreCase(quoteCurrency)
                    && BigDecimal.ONE.compareTo(line.getFxRate()) == 0) {
                // A foreign line at parity is almost always a rate nobody set.
                warnings.add("Line " + line.getLineNo() + " is in " + line.getCurrency()
                        + " with a rate of 1 towards " + quoteCurrency
                        + ": the conversion has not been set");
            }
            if (line.getFxRateAt() == null && !line.getCurrency().equalsIgnoreCase(quoteCurrency)) {
                warnings.add("Line " + line.getLineNo() + " carries no rate date");
            }

            BigDecimal amount = line.getQuantity()
                    .multiply(line.getUnitPrice())
                    .multiply(line.getFxRate())
                    .setScale(2, RoundingMode.HALF_UP);

            if (line.getKind().isDeduction()) {
                amount = amount.abs().negate();
                deductions = deductions.add(amount);
            }

            net = net.add(amount);
            byKind.merge(line.getKind().name(), amount, BigDecimal::add);
        }

        return new Totals(
                net.setScale(2, RoundingMode.HALF_UP),
                deductions.setScale(2, RoundingMode.HALF_UP),
                byKind,
                List.copyOf(currencies),
                warnings);
    }
}
