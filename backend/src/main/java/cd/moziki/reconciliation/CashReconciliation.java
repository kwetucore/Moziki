package cd.moziki.reconciliation;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One immutable declaration of what a leader physically counted at close of
 * business, next to a frozen snapshot of the journal balance. A correction is
 * a new revision; existing rows are protected by a PostgreSQL trigger.
 */
@Entity
@Table(name = "cash_reconciliations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashReconciliation {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "cooperative_id", nullable = false)
    private UUID cooperativeId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private int revision;

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "counted_cash_minor", nullable = false)
    private long countedCashMinor;

    @Column(name = "counted_airtel_minor", nullable = false)
    private long countedAirtelMinor;

    @Column(name = "counted_orange_minor", nullable = false)
    private long countedOrangeMinor;

    @Column(name = "counted_mpesa_minor", nullable = false)
    private long countedMpesaMinor;

    @Column(name = "book_cash_minor", nullable = false)
    private long bookCashMinor;

    @Column(name = "book_airtel_minor", nullable = false)
    private long bookAirtelMinor;

    @Column(name = "book_orange_minor", nullable = false)
    private long bookOrangeMinor;

    @Column(name = "book_mpesa_minor", nullable = false)
    private long bookMpesaMinor;

    @Column(name = "cash_difference_minor", nullable = false)
    private long cashDifferenceMinor;

    @Column(name = "airtel_difference_minor", nullable = false)
    private long airtelDifferenceMinor;

    @Column(name = "orange_difference_minor", nullable = false)
    private long orangeDifferenceMinor;

    @Column(name = "mpesa_difference_minor", nullable = false)
    private long mpesaDifferenceMinor;

    @Column(name = "total_counted_minor", nullable = false)
    private long totalCountedMinor;

    @Column(name = "total_book_minor", nullable = false)
    private long totalBookMinor;

    @Column(name = "total_difference_minor", nullable = false)
    private long totalDifferenceMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReconciliationStatus status;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
