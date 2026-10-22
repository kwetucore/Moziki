package cd.moziki.account;

import cd.moziki.account.dto.AccountResponse;
import cd.moziki.account.dto.ReconciliationRow;
import cd.moziki.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.list(principal);
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('LEADER','AUDITOR')")
    public List<ReconciliationRow> reconciliation(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.reconcile(principal);
    }
}
