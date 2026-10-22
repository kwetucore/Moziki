package cd.moziki.ledger;

import cd.moziki.common.ApiException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The first line of ledger defence. Structural rules are checked here before
 * anything is persisted; the PostgreSQL constraint trigger independently
 * re-checks the same invariants at commit time for every writer.
 */
@Component
public class DoubleEntryValidator {

    public void validate(List<LedgerLeg> legs, long declaredTotalMinor) {
        if (legs == null || legs.size() < 2) {
            throw ApiException.unprocessable("LEDGER_MIN_LINES", "error.ledger.min_lines");
        }

        Set<UUID> accounts = new HashSet<>();
        long debits = 0;
        long credits = 0;

        for (LedgerLeg leg : legs) {
            if (leg.accountId() == null || leg.direction() == null) {
                throw ApiException.unprocessable("LEDGER_INVALID_LEG", "error.ledger.invalid_leg");
            }
            if (leg.amountMinor() <= 0) {
                throw ApiException.unprocessable("LEDGER_INVALID_AMOUNT", "error.ledger.invalid_amount");
            }
            if (!accounts.add(leg.accountId())) {
                throw ApiException.unprocessable("LEDGER_DUPLICATE_ACCOUNT", "error.ledger.duplicate_account");
            }
            try {
                if (leg.direction() == EntryDirection.DEBIT) {
                    debits = Math.addExact(debits, leg.amountMinor());
                } else {
                    credits = Math.addExact(credits, leg.amountMinor());
                }
            } catch (ArithmeticException ex) {
                throw ApiException.unprocessable("LEDGER_AMOUNT_TOO_LARGE", "error.ledger.amount_too_large");
            }
        }

        if (debits != credits) {
            throw ApiException.unprocessable("LEDGER_UNBALANCED", "error.ledger.unbalanced");
        }
        if (debits == 0) {
            throw ApiException.unprocessable("LEDGER_INVALID_AMOUNT", "error.ledger.invalid_amount");
        }
        if (debits != declaredTotalMinor) {
            throw ApiException.unprocessable("LEDGER_TOTAL_MISMATCH", "error.ledger.total_mismatch");
        }
    }
}
