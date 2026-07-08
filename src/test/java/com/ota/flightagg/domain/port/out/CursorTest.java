package com.ota.flightagg.domain.port.out;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void encodesAndDecodesRoundTrip() {
        var cursor = new Cursor(new BigDecimal("129.62"), 4815162342L);

        var decoded = Cursor.decode(cursor.encode());

        assertThat(decoded.price()).isEqualByComparingTo("129.62");
        assertThat(decoded.id()).isEqualTo(4815162342L);
    }

    @Test
    void producesUrlSafeOpaqueToken() {
        var token = new Cursor(new BigDecimal("99.90"), 1L).encode();

        assertThat(token).doesNotContain("+", "/", "=", ":");
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> Cursor.decode("not-a-valid-cursor!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
