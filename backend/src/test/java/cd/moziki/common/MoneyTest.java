package cd.moziki.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void convertsCdfAmountsToMinorUnits() {
        assertThat(Money.toMinor(new BigDecimal("50000"))).isEqualTo(5_000_000L);
        assertThat(Money.toMinor(new BigDecimal("1234.56"))).isEqualTo(123_456L);
        assertThat(Money.toMinor(new BigDecimal("0.01"))).isEqualTo(1L);
    }

    @Test
    void allowsTrailingZeroesButRejectsSubCentPrecision() {
        assertThat(Money.toMinor(new BigDecimal("10.500"))).isEqualTo(1_050L);
        assertThatThrownBy(() -> Money.toMinor(new BigDecimal("10.555")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("AMOUNT_PRECISION");
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> Money.toMinor(BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.toMinor(new BigDecimal("-5"))).isInstanceOf(ApiException.class);
    }

    @Test
    void convertsBackWithoutRounding() {
        assertThat(Money.toDecimal(5_000_000L)).isEqualByComparingTo("50000.00");
        assertThat(Money.toDecimal(123_456L)).isEqualByComparingTo("1234.56");
    }
}
