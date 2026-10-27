package cd.moziki.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByCooperativeId(UUID cooperativeId, Pageable pageable);

    long countByCooperativeIdAndCreatedAtGreaterThanEqual(UUID cooperativeId, Instant since);

    long countByCooperativeIdAndActionAndCreatedAtGreaterThanEqual(
            UUID cooperativeId, String action, Instant since);
}
