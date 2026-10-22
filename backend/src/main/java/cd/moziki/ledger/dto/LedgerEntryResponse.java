package cd.moziki.ledger.dto;

import cd.moziki.ledger.EntryDirection;

import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        String accountCode,
        String accountName,
        EntryDirection direction,
        long amountMinor
) {
}
