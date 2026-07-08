package com.ota.flightagg.domain.service;

import com.ota.flightagg.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkupPolicyTest {

    private final MarkupPolicy fivePercent = MarkupPolicy.ofPercentage(new BigDecimal("5"));

    @Test
    void appliesFivePercentMarkup() {
        var result = fivePercent.applyTo(Money.of("100.00", "EUR"));

        assertThat(result.amount()).isEqualByComparingTo("105.00");
        assertThat(result.currency().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        var result = fivePercent.applyTo(Money.of("123.45", "EUR"));

        assertThat(result.amount()).isEqualByComparingTo("129.62");
        assertThat(result.amount().scale()).isEqualTo(2);
    }

    @Test
    void zeroPercentLeavesAmountUnchanged() {
        var result = MarkupPolicy.ofPercentage(BigDecimal.ZERO).applyTo(Money.of("77.77", "EUR"));

        assertThat(result.amount()).isEqualByComparingTo("77.77");
    }

    @Test
    void rejectsNegativePercentage() {
        assertThatThrownBy(() -> MarkupPolicy.ofPercentage(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
