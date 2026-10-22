package cd.moziki.ledger;

/** Where a transaction entered the system. USSD is reserved for the feature-phone phase. */
public enum TransactionSource {
    WEB,
    USSD,
    SYSTEM
}
