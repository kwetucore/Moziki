package cd.moziki.ledger;

import cd.moziki.common.Money;
import cd.moziki.common.PageResponse;
import cd.moziki.ledger.dto.PostTransactionRequest;
import cd.moziki.ledger.dto.ReversalRequest;
import cd.moziki.ledger.dto.TransactionResponse;
import cd.moziki.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final LedgerService ledgerService;
    private final LedgerQueryService queryService;

    public TransactionController(LedgerService ledgerService, LedgerQueryService queryService) {
        this.ledgerService = ledgerService;
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public TransactionResponse post(@Valid @RequestBody PostTransactionRequest request,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        PostTransactionCommand command = new PostTransactionCommand(
                request.kind(),
                request.memberId(),
                Money.toMinor(request.amount()),
                request.channel(),
                request.description(),
                request.externalRef(),
                request.idempotencyKey(),
                cd.moziki.ledger.TransactionSource.WEB
        );
        LedgerTransaction transaction = ledgerService.post(command, principal);
        return queryService.get(transaction.getId(), principal);
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public TransactionResponse reverse(@PathVariable UUID id,
                                       @Valid @RequestBody ReversalRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        LedgerTransaction reversal = ledgerService.reverse(id, request.reason(), principal);
        return queryService.get(reversal.getId(), principal);
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return queryService.list(principal, safePage, safeSize, memberId);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return queryService.get(id, principal);
    }
}
