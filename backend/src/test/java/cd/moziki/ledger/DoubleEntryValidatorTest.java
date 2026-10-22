package cd.moziki.ledger;

import cd.moziki.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoubleEntryValidatorTest {

    private final DoubleEntryValidator validator = new DoubleEntryValidator();

    private final UUID cash = UUID.randomUUID();
    private final UUID savings = UUID.randomUUID();

    @Test
    void acceptsBalancedJournalEntry() {
        List<LedgerLeg> legs = List.of(
                new LedgerLeg(cash, EntryDirection.DEBIT, 5_000_00),
                new LedgerLeg(savings, EntryDirection.CREDIT, 5_000_00)
        );

        assertThatCode(() -> validator.validate(legs, 5_000_00)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnbalancedEntry() {
        List<LedgerLeg> legs = List.of(
                new LedgerLeg(cash, EntryDirection.DEBIT, 5_000_00),
                new LedgerLeg(savings, EntryDirection.CREDIT, 4_999_00)
        );

        assertThatThrownBy(() -> validator.validate(legs, 5_000_00))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("LEDGER_UNBALANCED");
    }

    @Test
    void rejectsSingleLegEntry() {
        List<LedgerLeg> legs = List.of(new LedgerLeg(cash, EntryDirection.DEBIT, 1_000));

        assertThatThrownBy(() -> validator.validate(legs, 1_000))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("LEDGER_MIN_LINES");
    }

    @Test
    void rejectsSameAccountOnBothSides() {
        List<LedgerLeg> legs = List.of(
                new LedgerLeg(cash, EntryDirection.DEBIT, 1_000),
                new LedgerLeg(cash, EntryDirection.CREDIT, 1_000)
        );

        assertThatThrownBy(() -> validator.validate(legs, 1_000))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("LEDGER_DUPLICATE_ACCOUNT");
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        List<LedgerLeg> zero = List.of(
                new LedgerLeg(cash, EntryDirection.DEBIT, 0),
                new LedgerLeg(savings, EntryDirection.CREDIT, 0)
        );
        List<LedgerLeg> negative = List.of(
                new LedgerLeg(cash, EntryDirection.DEBIT, -500),
                new LedgerLeg(savings, EntryDirection.CREDIT, -500)
        );

        assertThatThrownBy(() -> validator.validate(zero, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(negative, -500)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsDeclaredTotalThatDoesNotMatchLines() {
        List<LedgerLeg> legs = List.of(
                new LedgerLeg(cash, EntryDirection.DEBIT, 2_000),
                new LedgerLeg(savings, EntryDirection.CREDIT, 2_000)
        );

        assertThatThrownBy(() -> validator.validate(legs, 3_000))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("LEDGER_TOTAL_MISMATCH");
    }

    @Test
    void acceptsMultiLegEntryWhenBothSidesBalance() {
        UUID fees = UUID.randomUUID();
        List<LedgerLeg> legs = List.of(
                new LedgerLeg(cash, EntryDirection.DEBIT, 10_000),
                new LedgerLeg(savings, EntryDirection.CREDIT, 9_500),
                new LedgerLeg(fees, EntryDirection.CREDIT, 500)
        );

        assertThatCode(() -> validator.validate(legs, 10_000)).doesNotThrowAnyException();
        assertThat(legs).hasSize(3);
    }
}
