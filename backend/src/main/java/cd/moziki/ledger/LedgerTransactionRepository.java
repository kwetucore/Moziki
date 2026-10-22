package cd.moziki.ledger;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByIdAndCooperativeId(UUID id, UUID cooperativeId);

    Optional<LedgerTransaction> findByCooperativeIdAndIdempotencyKey(UUID cooperativeId, String idempotencyKey);

    Optional<LedgerTransaction> findByReference(String reference);

    Page<LedgerTransaction> findByCooperativeId(UUID cooperativeId, Pageable pageable);

    Page<LedgerTransaction> findByCooperativeIdAndMemberId(UUID cooperativeId, UUID memberId, Pageable pageable);

    boolean existsByCooperativeIdAndReversesTransactionId(UUID cooperativeId, UUID reversesTransactionId);

    long countByCooperativeId(UUID cooperativeId);

    Optional<LedgerTransaction> findFirstByCooperativeIdOrderByCreatedAtDesc(UUID cooperativeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LedgerTransaction t where t.id = :id and t.cooperativeId = :cooperativeId")
    Optional<LedgerTransaction> findByIdAndCooperativeIdForUpdate(
            @Param("id") UUID id, @Param("cooperativeId") UUID cooperativeId);
}
