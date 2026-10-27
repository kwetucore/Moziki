package cd.moziki.transparency;

import cd.moziki.account.ChartOfAccountsService;
import cd.moziki.audit.AuditLog;
import cd.moziki.audit.AuditLogRepository;
import cd.moziki.audit.AuditService;
import cd.moziki.common.ApiException;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.reconciliation.CashReconciliation;
import cd.moziki.reconciliation.CashReconciliationRepository;
import cd.moziki.reconciliation.ReconciliationCalendar;
import cd.moziki.reconciliation.ReconciliationEvidence;
import cd.moziki.reconciliation.ReconciliationEvidenceRepository;
import cd.moziki.reconciliation.ReconciliationReview;
import cd.moziki.reconciliation.ReconciliationReviewRepository;
import cd.moziki.reconciliation.ReconciliationReviewStatus;
import cd.moziki.reconciliation.ReconciliationService;
import cd.moziki.reconciliation.ReconciliationStatus;
import cd.moziki.reconciliation.dto.EvidenceResponse;
import cd.moziki.security.UserPrincipal;
import cd.moziki.transparency.dto.TransparencySummary;
import cd.moziki.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class TransparencyService {

    private static final int WINDOW_DAYS = 30;

    private final CooperativeRepository cooperativeRepository;
    private final CashReconciliationRepository reconciliationRepository;
    private final ReconciliationReviewRepository reviewRepository;
    private final ReconciliationEvidenceRepository evidenceRepository;
    private final ReconciliationService reconciliationService;
    private final ReconciliationCalendar calendar;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public TransparencyService(CooperativeRepository cooperativeRepository,
                               CashReconciliationRepository reconciliationRepository,
                               ReconciliationReviewRepository reviewRepository,
                               ReconciliationEvidenceRepository evidenceRepository,
                               ReconciliationService reconciliationService,
                               ReconciliationCalendar calendar,
                               AuditService auditService,
                               AuditLogRepository auditLogRepository,
                               UserRepository userRepository) {
        this.cooperativeRepository = cooperativeRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.reviewRepository = reviewRepository;
        this.evidenceRepository = evidenceRepository;
        this.reconciliationService = reconciliationService;
        this.calendar = calendar;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public TransparencySummary summary(UserPrincipal principal) {
        Cooperative cooperative = cooperativeRepository.findById(principal.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));
        UUID cooperativeId = cooperative.getId();
        LocalDate today = calendar.today(cooperative);
        Instant since = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS);

        Map<String, Long> balances = reconciliationService.bookBalances(cooperative);
        TransparencySummary.LedgerSnapshot ledger = new TransparencySummary.LedgerSnapshot(
                balances.get(ChartOfAccountsService.CASH),
                balances.get(ChartOfAccountsService.AIRTEL_MONEY),
                balances.get(ChartOfAccountsService.ORANGE_MONEY),
                balances.get(ChartOfAccountsService.MPESA));

        List<CashReconciliation> recent = reconciliationRepository
                .findByCooperativeIdAndCreatedAtGreaterThanEqualOrderByBusinessDateDescRevisionDesc(
                        cooperativeId, since);

        Map<UUID, ReconciliationReview> reviews = reviews(recent);
        long pendingReviewCount = 0;

        // Only the first submission or an approved correction is authoritative.
        // Pending and rejected corrections are visible, but never move the
        // member-facing discrepancy metrics until an auditor accepts them.
        Map<LocalDate, CashReconciliation> authoritative = new TreeMap<>(Comparator.reverseOrder());
        for (CashReconciliation report : recent) {
            ReconciliationReviewStatus reviewStatus = ReconciliationService.reviewStatus(
                    report, reviews.get(report.getId()));
            if (reviewStatus == ReconciliationReviewStatus.PENDING) {
                pendingReviewCount++;
            }
            if (reviewStatus == ReconciliationReviewStatus.APPROVED) {
                authoritative.merge(report.getBusinessDate(), report,
                        (current, candidate) -> candidate.getRevision() > current.getRevision()
                                ? candidate : current);
            }
        }

        long balancedDays = authoritative.values().stream()
                .filter(report -> report.getStatus() == ReconciliationStatus.BALANCED)
                .count();
        long discrepancyDays = authoritative.values().stream()
                .filter(report -> report.getStatus() == ReconciliationStatus.DISCREPANCY)
                .count();
        long absoluteDifference = authoritative.values().stream()
                .mapToLong(report -> Math.abs(report.getTotalDifferenceMinor()))
                .sum();

        CashReconciliation latest = reconciliationRepository
                .findApproved(cooperativeId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);

        List<CashReconciliation> recentPage = reconciliationRepository
                .findByCooperativeIdOrderByBusinessDateDescRevisionDesc(
                        cooperativeId, PageRequest.of(0, 5))
                .getContent();

        Set<UUID> snapshotIds = new LinkedHashSet<>();
        snapshotIds.addAll(recentPage.stream().map(CashReconciliation::getId).toList());
        if (latest != null) {
            snapshotIds.add(latest.getId());
        }
        reviews.putAll(reviewsById(snapshotIds));
        Map<UUID, List<ReconciliationEvidence>> evidence = evidenceByReport(snapshotIds);

        Set<UUID> userIds = new LinkedHashSet<>();
        snapshotIds.forEach(id -> {
            CashReconciliation report = reportById(id, recentPage, latest);
            if (report != null) {
                userIds.add(report.getReportedBy());
            }
        });
        reviews.values().stream()
                .filter(review -> snapshotIds.contains(review.getReconciliationId()))
                .forEach(review -> userIds.add(review.getReviewerId()));
        evidence.values().forEach(list -> list.forEach(item -> userIds.add(item.getUploadedBy())));
        Map<UUID, String> names = names(userIds);

        List<TransparencySummary.ReconciliationSnapshot> recentSnapshots = new ArrayList<>();
        for (CashReconciliation report : recentPage) {
            recentSnapshots.add(snapshot(report, reviews.get(report.getId()), names, evidence));
        }

        List<AuditLog> auditPage = auditService.recent(cooperativeId,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")));
        Set<UUID> actorIds = new LinkedHashSet<>();
        auditPage.forEach(entry -> {
            if (entry.getActorId() != null) {
                actorIds.add(entry.getActorId());
            }
        });
        Map<UUID, String> actorNames = names(actorIds);
        List<TransparencySummary.Event> events = auditPage.stream()
                .map(entry -> new TransparencySummary.Event(
                        entry.getId(),
                        entry.getAction(),
                        entry.getActorId() == null ? null : actorNames.get(entry.getActorId()),
                        entry.getSummary(),
                        entry.getCreatedAt()))
                .toList();

        long auditEvents = auditLogRepository
                .countByCooperativeIdAndCreatedAtGreaterThanEqual(cooperativeId, since);
        long transactionsPosted = auditLogRepository
                .countByCooperativeIdAndActionAndCreatedAtGreaterThanEqual(
                        cooperativeId, "TRANSACTION_POSTED", since);
        long reversals = auditLogRepository
                .countByCooperativeIdAndActionAndCreatedAtGreaterThanEqual(
                        cooperativeId, "TRANSACTION_REVERSED", since);

        return new TransparencySummary(
                today,
                calendar.earliestAllowed(cooperative),
                calendar.lockHour(),
                authoritative.containsKey(today),
                pendingReviewCount,
                ledger,
                latest == null ? null : snapshot(latest, reviews.get(latest.getId()), names, evidence),
                recentSnapshots,
                authoritative.size(),
                balancedDays,
                discrepancyDays,
                absoluteDifference,
                auditEvents,
                transactionsPosted,
                reversals,
                events,
                !calendar.isLocked(cooperative, today)
        );
    }

    private Map<UUID, ReconciliationReview> reviews(Collection<CashReconciliation> reports) {
        return reviewsById(reports.stream().map(CashReconciliation::getId).toList());
    }

    private Map<UUID, ReconciliationReview> reviewsById(Collection<UUID> reportIds) {
        if (reportIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<UUID, ReconciliationReview> reviews = new HashMap<>();
        reviewRepository.findByReconciliationIdIn(reportIds)
                .forEach(review -> reviews.put(review.getReconciliationId(), review));
        return reviews;
    }

    private Map<UUID, List<ReconciliationEvidence>> evidenceByReport(Collection<UUID> reportIds) {
        if (reportIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ReconciliationEvidence>> evidence = new LinkedHashMap<>();
        evidenceRepository.findByReconciliationIdInOrderByCreatedAtAsc(reportIds)
                .forEach(item -> evidence
                        .computeIfAbsent(item.getReconciliationId(), key -> new ArrayList<>())
                        .add(item));
        return evidence;
    }

    private static CashReconciliation reportById(UUID id, List<CashReconciliation> recent,
                                                 CashReconciliation latest) {
        for (CashReconciliation report : recent) {
            if (report.getId().equals(id)) {
                return report;
            }
        }
        return latest != null && latest.getId().equals(id) ? latest : null;
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(ids)
                .forEach(user -> names.put(user.getId(), user.getFullName()));
        return names;
    }

    private static TransparencySummary.ReconciliationSnapshot snapshot(
            CashReconciliation report,
            ReconciliationReview review,
            Map<UUID, String> names,
            Map<UUID, List<ReconciliationEvidence>> evidenceByReport) {
        List<EvidenceResponse> evidence = evidenceByReport
                .getOrDefault(report.getId(), List.of())
                .stream()
                .map(item -> new EvidenceResponse(
                        item.getId(),
                        item.getFileName(),
                        item.getContentType(),
                        item.getSizeBytes(),
                        item.getSha256(),
                        item.getUploadedBy(),
                        names.get(item.getUploadedBy()),
                        item.getCreatedAt()))
                .toList();

        return new TransparencySummary.ReconciliationSnapshot(
                report.getId(),
                report.getBusinessDate(),
                report.getRevision(),
                report.getStatus(),
                report.getTotalCountedMinor(),
                report.getTotalBookMinor(),
                report.getTotalDifferenceMinor(),
                report.getCurrency(),
                names.get(report.getReportedBy()),
                report.getCreatedAt(),
                ReconciliationService.reviewStatus(report, review),
                review == null ? null : names.get(review.getReviewerId()),
                review == null ? null : review.getCreatedAt(),
                review == null ? null : review.getNote(),
                evidence);
    }
}

