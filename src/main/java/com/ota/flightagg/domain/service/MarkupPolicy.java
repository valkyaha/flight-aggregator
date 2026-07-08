package com.ota.flightagg.domain.service;

import com.ota.flightagg.domain.model.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record MarkupPolicy(BigDecimal percentage) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public MarkupPolicy {
        Objects.requireNonNull(percentage, "percentage must not be null");
        if (percentage.signum() < 0) {
            throw new IllegalArgumentException("percentage must not be negative: " + percentage);
        }
    }

    public static MarkupPolicy ofPercentage(BigDecimal percentage) {
        return new MarkupPolicy(percentage);
    }

    public Money applyTo(Money base) {
        Objects.requireNonNull(base, "base must not be null");
        var factor = BigDecimal.ONE.add(percentage.divide(HUNDRED));
        return new Money(base.amount().multiply(factor), base.currency());
    }
}
