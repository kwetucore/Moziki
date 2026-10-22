package cd.moziki.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal header. Never updated by the application except to mark a reversal;
 * the database trigger trg_transactions_immutable enforces the same rule for
 * every writer, including future USSD services and manual SQL.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "cooperative_id", nullable = false)
    private UUID cooperativeId;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TransactionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private TransactionSource source = TransactionSource.WEB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.POSTED;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "external_ref", length = 80)
    private String externalRef;

    /** Client-generated key used to make retries and offline sync safe. */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "reverses_transaction_id")
    private UUID reversesTransactionId;

    @Column(name = "reversal_reason", length = 300)
    private String reversalReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    public boolean isReversed() {
        return status == TransactionStatus.REVERSED;
    }
}
