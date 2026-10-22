package cd.moziki.dashboard;

import cd.moziki.account.AccountCategory;
import cd.moziki.account.AccountRepository;
import cd.moziki.common.PageResponse;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.cooperative.dto.CooperativeSummary;
import cd.moziki.dashboard.dto.DashboardResponse;
import cd.moziki.ledger.LedgerQueryService;
import cd.moziki.ledger.LedgerTransaction;
import cd.moziki.ledger.LedgerTransactionRepository;
import cd.moziki.ledger.dto.TransactionResponse;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DashboardService {

    private final CooperativeRepository cooperativeRepository;
    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LedgerQueryService ledgerQueryService;

    public DashboardService(CooperativeRepository cooperativeRepository,
                            AccountRepository accountRepository,
                            LedgerTransactionRepository transactionRepository,
                            UserRepository userRepository,
                            LedgerQueryService ledgerQueryService) {
        this.cooperativeRepository = cooperativeRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.ledgerQueryService = ledgerQueryService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UserPrincipal principal) {
        Cooperative cooperative = cooperativeRepository.findById(principal.getCooperativeId())
                .orElseThrow(() -> cd.moziki.common.ApiException.notFound(
                        "COOP_NOT_FOUND", "error.coop.not_found"));

        DashboardResponse.PoolTotals pool = new DashboardResponse.PoolTotals(
                accountRepository.sumBalanceByCategory(cooperative.getId(), AccountCategory.CASH),
                accountRepository.sumBalanceByCategory(cooperative.getId(), AccountCategory.MOBILE_MONEY),
                -accountRepository.sumBalanceByCategory(cooperative.getId(), AccountCategory.SAVINGS),
                -accountRepository.sumBalanceByCategory(cooperative.getId(), AccountCategory.SHARES),
                accountRepository.sumBalanceByCategory(cooperative.getId(), AccountCategory.LOANS),
                -accountRepository.sumBalanceByCategory(cooperative.getId(), AccountCategory.FEES)
        );

        DashboardResponse.PersonalTotals personal = new DashboardResponse.PersonalTotals(
                -accountRepository.sumBalanceByMemberAndCategory(
                        cooperative.getId(), principal.getId(), AccountCategory.SAVINGS),
                -accountRepository.sumBalanceByMemberAndCategory(
                        cooperative.getId(), principal.getId(), AccountCategory.SHARES)
        );

        LedgerTransaction latest = transactionRepository
                .findFirstByCooperativeIdOrderByCreatedAtDesc(cooperative.getId())
                .orElse(null);
        Instant lastTransactionAt = latest == null ? null : latest.getCreatedAt();

        PageResponse<TransactionResponse> recent = ledgerQueryService.list(principal, 0, 6, null);

        return new DashboardResponse(
                CooperativeSummary.from(cooperative),
                pool,
                personal,
                userRepository.countByCooperativeId(cooperative.getId()),
                transactionRepository.countByCooperativeId(cooperative.getId()),
                lastTransactionAt,
                recent
        );
    }
}
