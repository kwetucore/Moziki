package cd.moziki.ledger;

import java.util.UUID;

/** A proposed journal line before it is written. */
public record LedgerLeg(UUID accountId, EntryDirection direction, long amountMinor) {
}
