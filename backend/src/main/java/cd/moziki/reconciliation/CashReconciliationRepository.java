package cd.moziki.reconciliation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashReconciliationRepository extends JpaRepository<CashReconciliation, UUID> {

    Optional<CashReconciliation> findFirstByCooperativeIdOrderByBusinessDateDescRevisionDesc(
            UUID cooperativeId);

    Optional<CashReconciliation> findByIdAndCooperativeId(UUID id, UUID cooperativeId);

    Optional<CashReconciliation> findFirstByCooperativeIdAndBusinessDateOrderByRevisionDesc(
            UUID cooperativeId, LocalDate businessDate);

    Page<CashReconciliation> findByCooperativeIdOrderByBusinessDateDescRevisionDesc(
            UUID cooperativeId, Pageable pageable);

    List<CashReconciliation> findByCooperativeIdAndCreatedAtGreaterThanEqualOrderByBusinessDateDescRevisionDesc(
            UUID cooperativeId, Instant since);

    /** First submissions and auditor-approved corrections, newest business day first. */
    @Query("""
            SELECT r FROM CashReconciliation r
            WHERE r.cooperativeId = :cooperativeId
              AND (r.revision = 1 OR EXISTS (
                    SELECT 1 FROM ReconciliationReview v
                    WHERE v.reconciliationId = r.id
                      AND v.decision = cd.moziki.reconciliation.ReconciliationDecision.APPROVED))
            ORDER BY r.businessDate DESC, r.revision DESC
            """)
    List<CashReconciliation> findApproved(@Param("cooperativeId") UUID cooperativeId,
                                          Pageable pageable);
}
