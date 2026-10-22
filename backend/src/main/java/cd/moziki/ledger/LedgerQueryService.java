package cd.moziki.ledger;

import cd.moziki.account.Account;
import cd.moziki.account.AccountRepository;
import cd.moziki.common.ApiException;
import cd.moziki.common.PageResponse;
import cd.moziki.ledger.dto.LedgerEntryResponse;
import cd.moziki.ledger.dto.TransactionResponse;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.Role;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerQueryService {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public LedgerQueryService(LedgerTransactionRepository transactionRepository,
                              LedgerEntryRepository entryRepository,
                              AccountRepository accountRepository,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UserPrincipal principal, int page, int size,
                                                  UUID memberIdFilter) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LedgerTransaction> transactions;
        if (principal.getRole() == Role.MEMBER) {
            transactions = transactionRepository.findByCooperativeIdAndMemberId(
                    principal.getCooperativeId(), principal.getId(), pageable);
        } else if (memberIdFilter != null) {
            transactions = transactionRepository.findByCooperativeIdAndMemberId(
                    principal.getCooperativeId(), memberIdFilter, pageable);
        } else {
            transactions = transactionRepository.findByCooperativeId(
                    principal.getCooperativeId(), pageable);
        }

        Map<UUID, String> names = resolveUserNames(transactions.getContent());
        List<TransactionResponse> content = transactions.getContent().stream()
                .map(transaction -> map(transaction, names, null))
                .toList();
        return PageResponse.of(content, transactions.getNumber(), transactions.getSize(),
                transactions.getTotalElements(), transactions.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id, UserPrincipal principal) {
        LedgerTransaction transaction = transactionRepository
                .findByIdAndCooperativeId(id, principal.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "error.transaction.not_found"));

        if (principal.getRole() == Role.MEMBER
                && !principal.getId().equals(transaction.getMemberId())) {
            // Deliberately 404 so members cannot probe other members' references.
            throw ApiException.notFound("TRANSACTION_NOT_FOUND", "error.transaction.not_found");
        }

        Map<UUID, String> names = resolveUserNames(List.of(transaction));
        Map<UUID, Account> accounts = loadEntryAccounts(transaction.getId());
        return map(transaction, names, accounts);
    }

    public TransactionResponse map(LedgerTransaction transaction, Map<UUID, String> names,
                                   Map<UUID, Account> accounts) {
        List<LedgerEntryResponse> entries = null;
        if (accounts != null) {
            entries = entryRepository.findByTransactionIdOrderByLineNoAsc(transaction.getId()).stream()
                    .map(entry -> {
                        Account account = accounts.get(entry.getAccountId());
                        return new LedgerEntryResponse(entry.getId(), entry.getAccountId(),
                                account == null ? null : account.getCode(),
                                account == null ? null : account.getName(),
                                entry.getDirection(), entry.getAmountMinor());
                    })
                    .toList();
        }

        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getKind(),
                transaction.getChannel(),
                transaction.getSource(),
                transaction.getStatus(),
                transaction.getAmountMinor(),
                transaction.getCurrency(),
                transaction.getDescription(),
                transaction.getExternalRef(),
                transaction.getMemberId(),
                transaction.getMemberId() == null ? null : names.get(transaction.getMemberId()),
                transaction.getCreatedBy(),
                transaction.getCreatedBy() == null ? null : names.get(transaction.getCreatedBy()),
                transaction.getReversesTransactionId(),
                transaction.getReversalReason(),
                transaction.getReversedAt(),
                transaction.getCreatedAt(),
                entries
        );
    }

    private Map<UUID, Account> loadEntryAccounts(UUID transactionId) {
        List<UUID> accountIds = entryRepository.findByTransactionIdOrderByLineNoAsc(transactionId)
                .stream().map(LedgerEntry::getAccountId).toList();
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private Map<UUID, String> resolveUserNames(List<LedgerTransaction> transactions) {
        Set<UUID> ids = new HashSet<>();
        for (LedgerTransaction transaction : transactions) {
            if (transaction.getMemberId() != null) {
                ids.add(transaction.getMemberId());
            }
            if (transaction.getCreatedBy() != null) {
                ids.add(transaction.getCreatedBy());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getFullName()));
        return names;
    }
}
