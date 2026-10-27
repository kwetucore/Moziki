package cd.moziki.reconciliation.dto;

import cd.moziki.reconciliation.ReconciliationReviewStatus;
import cd.moziki.reconciliation.ReconciliationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CashReconciliationResponse(
        UUID id,
        LocalDate businessDate,
        int revision,
        UUID supersedesId,
        UUID reportedById,
        String reportedByName,
        String currency,
        ReconciliationStatus status,

        long countedCashMinor,
        long countedAirtelMinor,
        long countedOrangeMinor,
        long countedMpesaMinor,

        long bookCashMinor,
        long bookAirtelMinor,
        long bookOrangeMinor,
        long bookMpesaMinor,

        long cashDifferenceMinor,
        long airtelDifferenceMinor,
        long orangeDifferenceMinor,
        long mpesaDifferenceMinor,

        long totalCountedMinor,
        long totalBookMinor,
        long totalDifferenceMinor,
        String notes,
        Instant createdAt,

        ReconciliationReviewStatus reviewStatus,
        UUID reviewedById,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote,
        List<EvidenceResponse> evidence
) {
}
