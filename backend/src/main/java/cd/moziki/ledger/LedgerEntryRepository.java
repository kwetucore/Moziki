package cd.moziki.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionIdOrderByLineNoAsc(UUID transactionId);

    List<LedgerEntry> findByTransactionIdInOrderByTransactionIdAscLineNoAsc(Collection<UUID> transactionIds);

    @Query("""
            select coalesce(sum(case when e.direction = cd.moziki.ledger.EntryDirection.DEBIT
                                     then e.amountMinor else -e.amountMinor end), 0)
            from LedgerEntry e
            where e.accountId = :accountId
            """)
    long calculateBalance(@Param("accountId") UUID accountId);
}
