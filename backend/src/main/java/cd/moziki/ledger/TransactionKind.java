package cd.moziki.ledger;

public enum TransactionKind {
    DEPOSIT,
    WITHDRAWAL,
    SHARE_PURCHASE,
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    FEE,
    REVERSAL,
    ADJUSTMENT;

    /** Client-postable kinds; reversals and manual adjustments use dedicated flows. */
    public boolean isClientPostable() {
        return this != REVERSAL && this != ADJUSTMENT;
    }

    public boolean requiresMember() {
        return switch (this) {
            case DEPOSIT, WITHDRAWAL, SHARE_PURCHASE, LOAN_DISBURSEMENT, LOAN_REPAYMENT -> true;
            case FEE, REVERSAL, ADJUSTMENT -> false;
        };
    }
}
