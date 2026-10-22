package cd.moziki.account;

import java.util.UUID;

public interface AccountReconciliationProjection {
    UUID getAccountId();

    String getCode();

    String getName();

    long getCachedMinor();

    long getLedgerMinor();
}
