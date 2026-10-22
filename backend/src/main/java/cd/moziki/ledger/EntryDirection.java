package cd.moziki.ledger;

public enum EntryDirection {
    DEBIT,
    CREDIT;

    public EntryDirection opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
