package cd.moziki.reconciliation;

import cd.moziki.common.PageResponse;
import cd.moziki.reconciliation.dto.CashReconciliationResponse;
import cd.moziki.reconciliation.dto.EvidenceResponse;
import cd.moziki.reconciliation.dto.ReviewDecisionRequest;
import cd.moziki.reconciliation.dto.SubmitReconciliationRequest;
import cd.moziki.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/reconciliations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public CashReconciliationResponse submit(
            @Valid @RequestBody SubmitReconciliationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reconciliationService.submit(request, principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEADER','AUDITOR')")
    public PageResponse<CashReconciliationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return reconciliationService.list(principal, safePage, safeSize);
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyRole('LEADER','AUDITOR')")
    public CashReconciliationResponse get(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reconciliationService.get(reportId, principal);
    }

    /** An auditor approves or rejects a correction; the first count needs no review. */
    @PostMapping("/{reportId}/review")
    @PreAuthorize("hasRole('AUDITOR')")
    public CashReconciliationResponse review(
            @PathVariable UUID reportId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reconciliationService.review(reportId, request, principal);
    }

    @PostMapping(value = "/{reportId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public EvidenceResponse uploadEvidence(
            @PathVariable UUID reportId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reconciliationService.addEvidence(reportId, file, principal);
    }

    /** Any signed-in member of the cooperative can inspect the proof behind a count. */
    @GetMapping("/{reportId}/evidence/{evidenceId}")
    public ResponseEntity<Resource> downloadEvidence(
            @PathVariable UUID reportId,
            @PathVariable UUID evidenceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ReconciliationService.EvidenceDownload download =
                reconciliationService.downloadEvidence(reportId, evidenceId, principal);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.evidence().getContentType());
        } catch (IllegalArgumentException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String disposition = ContentDisposition.inline()
                .filename(download.evidence().getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }
}
