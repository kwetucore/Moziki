package cd.moziki.reconciliation.dto;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        UUID uploadedById,
        String uploadedByName,
        Instant createdAt
) {
}
