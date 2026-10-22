package cd.moziki.account.dto;

import java.util.UUID;

public record ReconciliationRow(
        UUID accountId,
        String code,
        String name,
        long cachedMinor,
        long ledgerMinor,
        long differenceMinor,
        boolean consistent
) {
}
