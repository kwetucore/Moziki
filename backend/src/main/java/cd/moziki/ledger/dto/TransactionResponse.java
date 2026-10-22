package cd.moziki.ledger.dto;

import cd.moziki.ledger.PaymentChannel;
import cd.moziki.ledger.TransactionKind;
import cd.moziki.ledger.TransactionSource;
import cd.moziki.ledger.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String reference,
        TransactionKind kind,
        PaymentChannel channel,
        TransactionSource source,
        TransactionStatus status,
        long amountMinor,
        String currency,
        String description,
        String externalRef,
        UUID memberId,
        String memberName,
        UUID createdBy,
        String createdByName,
        UUID reversesTransactionId,
        String reversalReason,
        Instant reversedAt,
        Instant createdAt,
        List<LedgerEntryResponse> entries
) {
}
