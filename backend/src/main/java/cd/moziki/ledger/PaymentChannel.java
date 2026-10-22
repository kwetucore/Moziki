package cd.moziki.ledger;

public enum PaymentChannel {
    CASH,
    AIRTEL_MONEY,
    ORANGE_MONEY,
    MPESA,
    BANK,
    INTERNAL;

    public boolean isMobileMoney() {
        return this == AIRTEL_MONEY || this == ORANGE_MONEY || this == MPESA;
    }
}
