package cd.moziki.transparency.dto;

import cd.moziki.reconciliation.ReconciliationReviewStatus;
import cd.moziki.reconciliation.ReconciliationStatus;
import cd.moziki.reconciliation.dto.EvidenceResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Member-facing accountability view. Deliberately excludes IP addresses,
 * user agents and raw audit metadata; those stay in the leader/auditor trail.
 */    public record TransparencySummary(
            LocalDate today,
            LocalDate earliestBusinessDate,
            int lockHour,
            boolean submittedToday,
            long pendingReviewCount,
            LedgerSnapshot ledger,
            ReconciliationSnapshot latestReconciliation,
            List<ReconciliationSnapshot> recentReconciliations,
            long reconciledDays,
            long balancedDays,
            long discrepancyDays,
            long absoluteDifferenceMinor,
            long auditEvents,
            long transactionsPosted,
            long reversals,
            List<Event> recentEvents,
            boolean beforeLock
    ) {

    public record LedgerSnapshot(
            long cashMinor,
            long airtelMinor,
            long orangeMinor,
            long mpesaMinor
    ) {
    }

    public record ReconciliationSnapshot(
            UUID id,
            LocalDate businessDate,
            int revision,
            ReconciliationStatus status,
            long totalCountedMinor,
            long totalBookMinor,
            long totalDifferenceMinor,
            String currency,
            String reportedByName,
            Instant createdAt,
            ReconciliationReviewStatus reviewStatus,
            String reviewedByName,
            Instant reviewedAt,
            String reviewNote,
            List<EvidenceResponse> evidence
    ) {
    }

    public record Event(
            UUID id,
            String action,
            String actorName,
            String summary,
            Instant createdAt
    ) {
    }
}
