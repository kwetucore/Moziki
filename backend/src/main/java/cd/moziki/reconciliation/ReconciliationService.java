package cd.moziki.reconciliation;

import cd.moziki.account.AccountReconciliationProjection;
import cd.moziki.account.AccountRepository;
import cd.moziki.account.ChartOfAccountsService;
import cd.moziki.audit.AuditService;
import cd.moziki.common.ApiException;
import cd.moziki.common.Money;
import cd.moziki.common.PageResponse;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.reconciliation.dto.CashReconciliationResponse;
import cd.moziki.reconciliation.dto.EvidenceResponse;
import cd.moziki.reconciliation.dto.ReviewDecisionRequest;
import cd.moziki.reconciliation.dto.SubmitReconciliationRequest;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The write and read model for daily cash counts. The count is compared with a
 * journal-derived snapshot, never with a mutable cached balance, so the report
 * remains an honest record of what the ledger said at that moment.
 *
 * <p>First submissions are authoritative immediately. Corrections are
 * append-only revisions that stay pending until an auditor approves or rejects
 * them; the previous approved revision remains the authoritative count in the
 * meantime.</p>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final int MAX_EVIDENCE_PER_REPORT = 5;

    private final CashReconciliationRepository repository;
    private final ReconciliationReviewRepository reviewRepository;
    private final ReconciliationEvidenceRepository evidenceRepository;
    private final EvidenceStorageService evidenceStorage;
    private final AccountRepository accountRepository;
    private final CooperativeRepository cooperativeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ReconciliationCalendar calendar;

    public ReconciliationService(CashReconciliationRepository repository,
                                 ReconciliationReviewRepository reviewRepository,
                                 ReconciliationEvidenceRepository evidenceRepository,
                                 EvidenceStorageService evidenceStorage,
                                 AccountRepository accountRepository,
                                 CooperativeRepository cooperativeRepository,
                                 UserRepository userRepository,
                                 AuditService auditService,
                                 ReconciliationCalendar calendar) {
        this.repository = repository;
        this.reviewRepository = reviewRepository;
        this.evidenceRepository = evidenceRepository;
        this.evidenceStorage = evidenceStorage;
        this.accountRepository = accountRepository;
        this.cooperativeRepository = cooperativeRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.calendar = calendar;
    }

    @Transactional
    public CashReconciliationResponse submit(SubmitReconciliationRequest request, UserPrincipal actor) {
        Cooperative cooperative = cooperativeRepository.findById(actor.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));

        LocalDate businessDate = calendar.validateBusinessDate(cooperative, request.businessDate());

        long countedCash = Money.toNonNegativeMinor(request.countedCash());
        long countedAirtel = Money.toNonNegativeMinor(request.countedAirtel());
        long countedOrange = Money.toNonNegativeMinor(request.countedOrange());
        long countedMpesa = Money.toNonNegativeMinor(request.countedMpesa());

        Map<String, Long> book = bookBalances(cooperative);
        long bookCash = book.get(ChartOfAccountsService.CASH);
        long bookAirtel = book.get(ChartOfAccountsService.AIRTEL_MONEY);
        long bookOrange = book.get(ChartOfAccountsService.ORANGE_MONEY);
        long bookMpesa = book.get(ChartOfAccountsService.MPESA);

        DiscrepancyCalculator.Breakdown totals = DiscrepancyCalculator.compare(
                countedCash, bookCash,
                countedAirtel, bookAirtel,
                countedOrange, bookOrange,
                countedMpesa, bookMpesa);

        CashReconciliation previous = repository
                .findFirstByCooperativeIdAndBusinessDateOrderByRevisionDesc(
                        cooperative.getId(), businessDate)
                .orElse(null);
        if (previous != null) {
            ReconciliationReview previousReview = reviewRepository
                    .findByReconciliationIdAndCooperativeId(previous.getId(), cooperative.getId())
                    .orElse(null);
            if (reviewStatus(previous, previousReview) == ReconciliationReviewStatus.PENDING) {
                throw ApiException.conflict("RECONCILIATION_PENDING_REVIEW",
                        "error.reconciliation.pending_review");
            }
        }
        int revision = previous == null ? 1 : previous.getRevision() + 1;

        CashReconciliation report = CashReconciliation.builder()
                .cooperativeId(cooperative.getId())
                .businessDate(businessDate)
                .revision(revision)
                .supersedesId(previous == null ? null : previous.getId())
                .reportedBy(actor.getId())
                .currency(cooperative.getCurrency())
                .countedCashMinor(countedCash)
                .countedAirtelMinor(countedAirtel)
                .countedOrangeMinor(countedOrange)
                .countedMpesaMinor(countedMpesa)
                .bookCashMinor(bookCash)
                .bookAirtelMinor(bookAirtel)
                .bookOrangeMinor(bookOrange)
                .bookMpesaMinor(bookMpesa)
                .cashDifferenceMinor(totals.cashDifferenceMinor())
                .airtelDifferenceMinor(totals.airtelDifferenceMinor())
                .orangeDifferenceMinor(totals.orangeDifferenceMinor())
                .mpesaDifferenceMinor(totals.mpesaDifferenceMinor())
                .totalCountedMinor(totals.totalCountedMinor())
                .totalBookMinor(totals.totalBookMinor())
                .totalDifferenceMinor(totals.totalDifferenceMinor())
                .status(totals.status())
                .notes(trimToNull(request.notes()))
                .build();
        repository.saveAndFlush(report);

        ReconciliationReviewStatus reviewStatus = reviewStatus(report, null);
        String action = previous == null
                ? "CASH_RECONCILIATION_SUBMITTED"
                : "CASH_RECONCILIATION_CORRECTED";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("businessDate", businessDate.toString());
        metadata.put("revision", revision);
        metadata.put("status", totals.status().name());
        metadata.put("reviewStatus", reviewStatus.name());
        metadata.put("currency", cooperative.getCurrency());
        metadata.put("totalCountedMinor", totals.totalCountedMinor());
        metadata.put("totalBookMinor", totals.totalBookMinor());
        metadata.put("totalDifferenceMinor", totals.totalDifferenceMinor());
        metadata.put("cashDifferenceMinor", totals.cashDifferenceMinor());
        metadata.put("airtelDifferenceMinor", totals.airtelDifferenceMinor());
        metadata.put("orangeDifferenceMinor", totals.orangeDifferenceMinor());
        metadata.put("mpesaDifferenceMinor", totals.mpesaDifferenceMinor());
        if (previous != null) {
            metadata.put("supersedes", previous.getId().toString());
        }
        auditService.record(cooperative.getId(), actor.getId(), action,
                "CASH_RECONCILIATION", report.getId(),
                "Cash count %s · revision %d · %s".formatted(
                        businessDate, revision, totals.status()),
                metadata);

        log.info("Cash reconciliation {} revision={} status={} review={} totalDiff={} {} coop={} by={}",
                businessDate, revision, totals.status(), reviewStatus, totals.totalDifferenceMinor(),
                cooperative.getCurrency(), cooperative.getCode(), actor.getId());

        return toResponse(report, Map.of(actor.getId(), actor.getFullName()), null);
    }

    @Transactional
    public CashReconciliationResponse review(UUID reportId, ReviewDecisionRequest request,
                                             UserPrincipal actor) {
        Cooperative cooperative = cooperativeRepository.findById(actor.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));

        CashReconciliation report = repository
                .findByIdAndCooperativeId(reportId, cooperative.getId())
                .orElseThrow(() -> ApiException.notFound("RECONCILIATION_NOT_FOUND",
                        "error.reconciliation.not_found"));

        if (report.getRevision() <= 1) {
            throw ApiException.conflict("RECONCILIATION_NOT_CORRECTION",
                    "error.reconciliation.not_correction");
        }
        if (reviewRepository.findByReconciliationIdAndCooperativeId(
                reportId, cooperative.getId()).isPresent()) {
            throw ApiException.conflict("RECONCILIATION_ALREADY_REVIEWED",
                    "error.reconciliation.already_reviewed");
        }
        String note = trimToNull(request.note());
        if (request.decision() == ReconciliationDecision.REJECTED && note == null) {
            throw ApiException.badRequest("RECONCILIATION_REJECTION_NOTE_REQUIRED",
                    "error.reconciliation.rejection_note_required");
        }

        ReconciliationReview review = ReconciliationReview.builder()
                .cooperativeId(cooperative.getId())
                .reconciliationId(reportId)
                .decision(request.decision())
                .reviewerId(actor.getId())
                .note(note)
                .build();
        reviewRepository.saveAndFlush(review);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("businessDate", report.getBusinessDate().toString());
        metadata.put("revision", report.getRevision());
        metadata.put("decision", review.getDecision().name());
        metadata.put("totalDifferenceMinor", report.getTotalDifferenceMinor());
        if (note != null) {
            metadata.put("note", note);
        }
        auditService.record(cooperative.getId(), actor.getId(),
                review.getDecision() == ReconciliationDecision.APPROVED
                        ? "CASH_RECONCILIATION_APPROVED"
                        : "CASH_RECONCILIATION_REJECTED",
                "CASH_RECONCILIATION", reportId,
                "Correction %s revision %d %s".formatted(
                        report.getBusinessDate(), report.getRevision(),
                        review.getDecision().name().toLowerCase()),
                metadata);

        log.info("Cash reconciliation review report={} decision={} by={}",
                reportId, review.getDecision(), actor.getId());

        return toResponse(report, Map.of(actor.getId(), actor.getFullName()), review);
    }

    @Transactional
    public EvidenceResponse addEvidence(UUID reportId, MultipartFile file, UserPrincipal actor) {
        Cooperative cooperative = cooperativeRepository.findById(actor.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));
        CashReconciliation report = repository
                .findByIdAndCooperativeId(reportId, cooperative.getId())
                .orElseThrow(() -> ApiException.notFound("RECONCILIATION_NOT_FOUND",
                        "error.reconciliation.not_found"));

        if (evidenceRepository.countByReconciliationId(reportId) >= MAX_EVIDENCE_PER_REPORT) {
            throw ApiException.conflict("EVIDENCE_LIMIT_REACHED", "error.evidence.limit",
                    MAX_EVIDENCE_PER_REPORT);
        }

        EvidenceStorageService.StoredFile stored = evidenceStorage.store(file);
        try {
            ReconciliationEvidence evidence = ReconciliationEvidence.builder()
                    .cooperativeId(cooperative.getId())
                    .reconciliationId(reportId)
                    .fileName(stored.fileName())
                    .contentType(stored.contentType())
                    .sizeBytes(stored.sizeBytes())
                    .sha256(stored.sha256())
                    .storageKey(stored.storageKey())
                    .uploadedBy(actor.getId())
                    .build();
            evidenceRepository.saveAndFlush(evidence);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("businessDate", report.getBusinessDate().toString());
            metadata.put("revision", report.getRevision());
            metadata.put("fileName", stored.fileName());
            metadata.put("contentType", stored.contentType());
            metadata.put("sizeBytes", stored.sizeBytes());
            metadata.put("sha256", stored.sha256());
            auditService.record(cooperative.getId(), actor.getId(),
                    "RECONCILIATION_EVIDENCE_ADDED", "CASH_RECONCILIATION", reportId,
                    "Evidence attached to cash count %s revision %d".formatted(
                            report.getBusinessDate(), report.getRevision()),
                    metadata);

            return new EvidenceResponse(evidence.getId(), evidence.getFileName(),
                    evidence.getContentType(), evidence.getSizeBytes(), evidence.getSha256(),
                    evidence.getUploadedBy(), actor.getFullName(), evidence.getCreatedAt());
        } catch (RuntimeException ex) {
            evidenceStorage.deleteQuietly(stored.storageKey());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public EvidenceDownload downloadEvidence(UUID reportId, UUID evidenceId, UserPrincipal actor) {
        ReconciliationEvidence evidence = evidenceRepository
                .findByIdAndReconciliationIdAndCooperativeId(
                        evidenceId, reportId, actor.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("EVIDENCE_NOT_FOUND",
                        "error.evidence.not_found"));
        Resource resource = evidenceStorage.load(evidence.getStorageKey());
        return new EvidenceDownload(evidence, resource);
    }

    @Transactional(readOnly = true)
    public CashReconciliationResponse get(UUID reportId, UserPrincipal principal) {
        Cooperative cooperative = cooperativeRepository.findById(principal.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));
        CashReconciliation report = repository
                .findByIdAndCooperativeId(reportId, cooperative.getId())
                .orElseThrow(() -> ApiException.notFound("RECONCILIATION_NOT_FOUND",
                        "error.reconciliation.not_found"));
        ReconciliationReview review = reviewRepository
                .findByReconciliationIdAndCooperativeId(reportId, cooperative.getId())
                .orElse(null);
        return toResponse(report, Map.of(), review);
    }

    @Transactional(readOnly = true)
    public PageResponse<CashReconciliationResponse> list(UserPrincipal principal, int page, int size) {
        Page<CashReconciliation> reports = repository
                .findByCooperativeIdOrderByBusinessDateDescRevisionDesc(
                        principal.getCooperativeId(), PageRequest.of(page, size));

        return PageResponse.of(toResponses(reports.getContent()), reports.getNumber(),
                reports.getSize(), reports.getTotalElements(), reports.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> bookBalances(Cooperative cooperative) {
        Map<String, Long> balances = new HashMap<>();
        for (AccountReconciliationProjection row : accountRepository.reconcile(cooperative.getId())) {
            balances.put(row.getCode(), row.getLedgerMinor());
        }
        for (String code : List.of(ChartOfAccountsService.CASH, ChartOfAccountsService.AIRTEL_MONEY,
                ChartOfAccountsService.ORANGE_MONEY, ChartOfAccountsService.MPESA)) {
            if (!balances.containsKey(code)) {
                throw ApiException.unprocessable("RECONCILIATION_ACCOUNT_MISSING",
                        "error.account.not_found");
            }
        }
        return balances;
    }

    public static ReconciliationReviewStatus reviewStatus(CashReconciliation report,
                                                          ReconciliationReview review) {
        if (report.getRevision() <= 1) {
            return ReconciliationReviewStatus.APPROVED;
        }
        if (review == null) {
            return ReconciliationReviewStatus.PENDING;
        }
        return review.getDecision() == ReconciliationDecision.APPROVED
                ? ReconciliationReviewStatus.APPROVED
                : ReconciliationReviewStatus.REJECTED;
    }

    private List<CashReconciliationResponse> toResponses(List<CashReconciliation> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = reports.stream().map(CashReconciliation::getId).toList();

        Map<UUID, ReconciliationReview> reviews = new HashMap<>();
        reviewRepository.findByReconciliationIdIn(ids)
                .forEach(review -> reviews.put(review.getReconciliationId(), review));

        Map<UUID, List<ReconciliationEvidence>> evidenceByReport = new HashMap<>();
        evidenceRepository.findByReconciliationIdInOrderByCreatedAtAsc(ids)
                .forEach(evidence -> evidenceByReport
                        .computeIfAbsent(evidence.getReconciliationId(), key -> new ArrayList<>())
                        .add(evidence));

        Set<UUID> userIds = new LinkedHashSet<>();
        reports.forEach(report -> userIds.add(report.getReportedBy()));
        reviews.values().forEach(review -> userIds.add(review.getReviewerId()));
        evidenceByReport.values().forEach(list ->
                list.forEach(evidence -> userIds.add(evidence.getUploadedBy())));
        Map<UUID, String> names = names(userIds);

        return reports.stream()
                .map(report -> buildResponse(report, names, reviews.get(report.getId()),
                        evidenceByReport.getOrDefault(report.getId(), List.of())))
                .toList();
    }

    private CashReconciliationResponse toResponse(CashReconciliation report,
                                                  Map<UUID, String> names,
                                                  ReconciliationReview review) {
        List<ReconciliationEvidence> evidence = evidenceRepository
                .findByReconciliationIdOrderByCreatedAtAsc(report.getId());
        Set<UUID> userIds = new LinkedHashSet<>();
        userIds.add(report.getReportedBy());
        if (review != null) {
            userIds.add(review.getReviewerId());
        }
        evidence.forEach(item -> userIds.add(item.getUploadedBy()));
        Map<UUID, String> allNames = new HashMap<>(names);
        allNames.putAll(names(userIds));
        return buildResponse(report, allNames, review, evidence);
    }

    private static CashReconciliationResponse buildResponse(
            CashReconciliation report,
            Map<UUID, String> names,
            ReconciliationReview review,
            List<ReconciliationEvidence> evidence) {
        List<EvidenceResponse> evidenceResponses = evidence.stream()
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

        return new CashReconciliationResponse(
                report.getId(),
                report.getBusinessDate(),
                report.getRevision(),
                report.getSupersedesId(),
                report.getReportedBy(),
                names.get(report.getReportedBy()),
                report.getCurrency(),
                report.getStatus(),
                report.getCountedCashMinor(),
                report.getCountedAirtelMinor(),
                report.getCountedOrangeMinor(),
                report.getCountedMpesaMinor(),
                report.getBookCashMinor(),
                report.getBookAirtelMinor(),
                report.getBookOrangeMinor(),
                report.getBookMpesaMinor(),
                report.getCashDifferenceMinor(),
                report.getAirtelDifferenceMinor(),
                report.getOrangeDifferenceMinor(),
                report.getMpesaDifferenceMinor(),
                report.getTotalCountedMinor(),
                report.getTotalBookMinor(),
                report.getTotalDifferenceMinor(),
                report.getNotes(),
                report.getCreatedAt(),
                reviewStatus(report, review),
                review == null ? null : review.getReviewerId(),
                review == null ? null : names.get(review.getReviewerId()),
                review == null ? null : review.getCreatedAt(),
                review == null ? null : review.getNote(),
                evidenceResponses);
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

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record EvidenceDownload(ReconciliationEvidence evidence, Resource resource) {
    }
}
