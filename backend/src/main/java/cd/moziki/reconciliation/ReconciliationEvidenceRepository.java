package cd.moziki.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationEvidenceRepository
        extends JpaRepository<ReconciliationEvidence, UUID> {

    List<ReconciliationEvidence> findByReconciliationIdOrderByCreatedAtAsc(UUID reconciliationId);

    List<ReconciliationEvidence> findByReconciliationIdInOrderByCreatedAtAsc(
            Collection<UUID> reconciliationIds);

    Optional<ReconciliationEvidence> findByIdAndReconciliationIdAndCooperativeId(
            UUID id, UUID reconciliationId, UUID cooperativeId);

    long countByReconciliationId(UUID reconciliationId);
}
