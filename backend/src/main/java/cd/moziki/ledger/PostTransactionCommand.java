package cd.moziki.ledger;

import java.util.UUID;

public record PostTransactionCommand(
        TransactionKind kind,
        UUID memberId,
        long amountMinor,
        PaymentChannel channel,
        String description,
        String externalRef,
        String idempotencyKey,
        TransactionSource source
) {
    public PostTransactionCommand withSource(TransactionSource newSource) {
        return new PostTransactionCommand(kind, memberId, amountMinor, channel, description,
                externalRef, idempotencyKey, newSource);
    }
}
