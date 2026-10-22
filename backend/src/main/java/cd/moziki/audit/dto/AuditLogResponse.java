package cd.moziki.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorName,
        String action,
        String entityType,
        UUID entityId,
        String summary,
        Map<String, Object> metadata,
        String ipAddress,
        Instant createdAt
) {
}
