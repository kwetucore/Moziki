package cd.moziki.account;

import cd.moziki.ledger.EntryDirection;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * A ledger account. balanceMinor is signed (debit-positive, the accounting
 * convention): assets grow with debits, liabilities/equity/income grow with
 * credits. UI-facing balances flip the sign for credit-normal accounts.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "cooperative_id", nullable = false)
    private UUID cooperativeId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccountCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_provider", length = 16)
    private WalletProvider walletProvider;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "CDF";

    @Column(name = "balance_minor", nullable = false)
    @Builder.Default
    private long balanceMinor = 0L;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean systemAccount = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Positive for credit-normal accounts (liabilities, equity, income). */
    public boolean isCreditNormal() {
        return type == AccountType.LIABILITY || type == AccountType.EQUITY || type == AccountType.INCOME;
    }

    /** Human-facing balance: savings/shares/income are shown as positive credits. */
    public long displayBalanceMinor() {
        return isCreditNormal() ? -balanceMinor : balanceMinor;
    }

    public void apply(EntryDirection direction, long amountMinor) {
        this.balanceMinor = direction == EntryDirection.DEBIT
                ? Math.addExact(this.balanceMinor, amountMinor)
                : Math.subtractExact(this.balanceMinor, amountMinor);
    }
}
