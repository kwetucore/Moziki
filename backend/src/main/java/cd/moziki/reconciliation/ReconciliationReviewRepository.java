package cd.moziki.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationReviewRepository extends JpaRepository<ReconciliationReview, UUID> {

    Optional<ReconciliationReview> findByReconciliationIdAndCooperativeId(
            UUID reconciliationId, UUID cooperativeId);

    List<ReconciliationReview> findByReconciliationIdIn(Collection<UUID> reconciliationIds);
}
