package cd.moziki.account;

import cd.moziki.account.dto.AccountResponse;
import cd.moziki.account.dto.ReconciliationRow;
import cd.moziki.common.ApiException;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.Role;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UserPrincipal principal) {
        List<Account> accounts = principal.getRole() == Role.MEMBER
                ? accountRepository.findByCooperativeIdAndMemberIdOrderByCodeAsc(
                        principal.getCooperativeId(), principal.getId())
                : accountRepository.findByCooperativeIdOrderByCodeAsc(principal.getCooperativeId());

        Map<UUID, User> members = userRepository
                .findByCooperativeIdOrderByFullNameAsc(principal.getCooperativeId())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return accounts.stream().map(account -> map(account, members)).toList();
    }

    /** Compares every cached balance with a fresh SUM over the immutable journal. */
    @Transactional(readOnly = true)
    public List<ReconciliationRow> reconcile(UserPrincipal principal) {
        return accountRepository.reconcile(principal.getCooperativeId()).stream()
                .map(row -> {
                    long difference = row.getCachedMinor() - row.getLedgerMinor();
                    return new ReconciliationRow(row.getAccountId(), row.getCode(), row.getName(),
                            row.getCachedMinor(), row.getLedgerMinor(), difference, difference == 0);
                })
                .toList();
    }

    private AccountResponse map(Account account, Map<UUID, User> members) {
        User member = account.getMemberId() == null ? null : members.get(account.getMemberId());
        return new AccountResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getCategory(),
                account.getWalletProvider(),
                account.getCurrency(),
                account.getBalanceMinor(),
                account.displayBalanceMinor(),
                account.getMemberId(),
                member == null ? null : member.getFullName(),
                account.isSystemAccount()
        );
    }

    public Account requireAccount(UUID cooperativeId, String code) {
        return accountRepository.findByCooperativeIdAndCode(cooperativeId, code)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "error.account.not_found"));
    }
}
