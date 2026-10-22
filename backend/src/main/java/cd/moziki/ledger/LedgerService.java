package cd.moziki.ledger;

import cd.moziki.account.Account;
import cd.moziki.account.AccountCategory;
import cd.moziki.account.AccountRepository;
import cd.moziki.account.WalletProvider;
import cd.moziki.audit.AuditService;
import cd.moziki.common.ApiException;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The only write path into the ledger.
 *
 * A posting is atomic: journal header, balanced lines, cached account balances
 * and the audit record are committed together or not at all. The PostgreSQL
 * deferred constraint trigger re-verifies the balance independently, so even a
 * future USSD gateway or a manual SQL writer cannot create an unbalanced entry.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CooperativeRepository cooperativeRepository;
    private final DoubleEntryValidator validator;
    private final ReferenceNumbers referenceNumbers;
    private final AuditService auditService;

    public LedgerService(LedgerTransactionRepository transactionRepository,
                         LedgerEntryRepository entryRepository,
                         AccountRepository accountRepository,
                         UserRepository userRepository,
                         CooperativeRepository cooperativeRepository,
                         DoubleEntryValidator validator,
                         ReferenceNumbers referenceNumbers,
                         AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.cooperativeRepository = cooperativeRepository;
        this.validator = validator;
        this.referenceNumbers = referenceNumbers;
        this.auditService = auditService;
    }

    @Transactional
    public LedgerTransaction post(PostTransactionCommand command, UserPrincipal actor) {
        Cooperative cooperative = cooperativeRepository.findById(actor.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));

        if (command.kind() == null || !command.kind().isClientPostable()) {
            throw ApiException.unprocessable("UNSUPPORTED_KIND", "error.ledger.unsupported_kind");
        }
        if (command.channel() == null || command.channel() == PaymentChannel.BANK
                || command.channel() == PaymentChannel.INTERNAL) {
            throw ApiException.unprocessable("UNSUPPORTED_CHANNEL", "error.ledger.unsupported_channel");
        }
        if (command.amountMinor() <= 0) {
            throw ApiException.unprocessable("LEDGER_INVALID_AMOUNT", "error.ledger.invalid_amount");
        }
        if (!StringUtils.hasText(command.description())) {
            throw ApiException.badRequest("DESCRIPTION_REQUIRED", "validation.required");
        }

        String idempotencyKey = StringUtils.hasText(command.idempotencyKey())
                ? command.idempotencyKey().trim()
                : null;

        if (idempotencyKey != null) {
            LedgerTransaction existing = transactionRepository
                    .findByCooperativeIdAndIdempotencyKey(cooperative.getId(), idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return replay(existing, command);
            }
        }

        User member = resolveMember(command, cooperative);
        Account cashAccount = resolveChannelAccount(cooperative, command.channel());
        Account savings = null;
        Account shares = null;
        Account loans = null;
        Account feeIncome = null;

        List<LedgerLeg> legs = new ArrayList<>(2);
        switch (command.kind()) {
            case DEPOSIT -> {
                savings = memberAccount(cooperative, member, AccountCategory.SAVINGS);
                legs.add(debit(cashAccount, command.amountMinor()));
                legs.add(credit(savings, command.amountMinor()));
            }
            case WITHDRAWAL -> {
                savings = memberAccount(cooperative, member, AccountCategory.SAVINGS);
                legs.add(debit(savings, command.amountMinor()));
                legs.add(credit(cashAccount, command.amountMinor()));
            }
            case SHARE_PURCHASE -> {
                shares = memberAccount(cooperative, member, AccountCategory.SHARES);
                legs.add(debit(cashAccount, command.amountMinor()));
                legs.add(credit(shares, command.amountMinor()));
            }
            case LOAN_DISBURSEMENT -> {
                loans = systemAccount(cooperative, AccountCategory.LOANS, null);
                legs.add(debit(loans, command.amountMinor()));
                legs.add(credit(cashAccount, command.amountMinor()));
            }
            case LOAN_REPAYMENT -> {
                loans = systemAccount(cooperative, AccountCategory.LOANS, null);
                legs.add(debit(cashAccount, command.amountMinor()));
                legs.add(credit(loans, command.amountMinor()));
            }
            case FEE -> {
                feeIncome = systemAccount(cooperative, AccountCategory.FEES, null);
                legs.add(debit(cashAccount, command.amountMinor()));
                legs.add(credit(feeIncome, command.amountMinor()));
            }
            default -> throw ApiException.unprocessable("UNSUPPORTED_KIND", "error.ledger.unsupported_kind");
        }

        validator.validate(legs, command.amountMinor());

        Map<UUID, Account> locked = lockInStableOrder(legs);
        for (LedgerLeg leg : legs) {
            Account account = locked.get(leg.accountId());
            assertAccountBelongsToCooperative(account, cooperative);
            assertFundsAvailable(account, leg);
        }

        Instant now = Instant.now();
        LedgerTransaction transaction = LedgerTransaction.builder()
                .cooperativeId(cooperative.getId())
                .reference(referenceNumbers.nextTransactionReference())
                .kind(command.kind())
                .channel(command.channel())
                .source(command.source() == null ? TransactionSource.WEB : command.source())
                .status(TransactionStatus.POSTED)
                .amountMinor(command.amountMinor())
                .currency(cooperative.getCurrency())
                .description(command.description().trim())
                .externalRef(trimToNull(command.externalRef()))
                .idempotencyKey(idempotencyKey)
                .memberId(member == null ? null : member.getId())
                .createdBy(actor.getId())
                .postedAt(now)
                .build();
        transactionRepository.save(transaction);

        writeEntries(transaction, legs);
        applyBalances(locked, legs);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reference", transaction.getReference());
        metadata.put("kind", transaction.getKind().name());
        metadata.put("channel", transaction.getChannel().name());
        metadata.put("amountMinor", transaction.getAmountMinor());
        metadata.put("currency", transaction.getCurrency());
        if (member != null) {
            metadata.put("memberNumber", member.getMemberNumber());
        }
        if (transaction.getExternalRef() != null) {
            metadata.put("externalRef", transaction.getExternalRef());
        }
        auditService.record(cooperative.getId(), actor.getId(), "TRANSACTION_POSTED",
                "TRANSACTION", transaction.getId(),
                "%s %s · %s".formatted(transaction.getKind(), transaction.getReference(),
                        transaction.getDescription()),
                metadata);

        log.info("Posted {} {} amount={} {} coop={} by={}", transaction.getKind(),
                transaction.getReference(), transaction.getAmountMinor(),
                transaction.getCurrency(), cooperative.getCode(), actor.getId());
        return transaction;
    }

    /**
     * Reversal posts the mirror image of the original entry. The original row
     * stays in the journal with status REVERSED; nothing is deleted.
     */
    @Transactional
    public LedgerTransaction reverse(UUID transactionId, String reason, UserPrincipal actor) {
        Cooperative cooperative = cooperativeRepository.findById(actor.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));
        if (!StringUtils.hasText(reason)) {
            throw ApiException.badRequest("REVERSAL_REASON_REQUIRED", "validation.required");
        }

        LedgerTransaction original = transactionRepository
                .findByIdAndCooperativeIdForUpdate(transactionId, cooperative.getId())
                .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "error.transaction.not_found"));

        if (original.getStatus() == TransactionStatus.REVERSED
                || transactionRepository.existsByCooperativeIdAndReversesTransactionId(
                        cooperative.getId(), original.getId())) {
            throw ApiException.conflict("ALREADY_REVERSED", "error.ledger.already_reversed");
        }
        if (original.getKind() == TransactionKind.REVERSAL) {
            throw ApiException.unprocessable("REVERSAL_OF_REVERSAL", "error.ledger.reversal_of_reversal");
        }

        List<LedgerEntry> originalEntries = entryRepository
                .findByTransactionIdOrderByLineNoAsc(original.getId());
        if (originalEntries.isEmpty()) {
            throw ApiException.unprocessable("NOT_REVERSIBLE", "error.ledger.not_reversible");
        }

        List<LedgerLeg> reversalLegs = originalEntries.stream()
                .map(entry -> new LedgerLeg(entry.getAccountId(), entry.getDirection().opposite(),
                        entry.getAmountMinor()))
                .toList();
        validator.validate(reversalLegs, original.getAmountMinor());

        Map<UUID, Account> locked = lockInStableOrder(reversalLegs);
        for (LedgerLeg leg : reversalLegs) {
            assertAccountBelongsToCooperative(locked.get(leg.accountId()), cooperative);
        }

        Instant now = Instant.now();
        LedgerTransaction reversal = LedgerTransaction.builder()
                .cooperativeId(cooperative.getId())
                .reference(referenceNumbers.nextTransactionReference())
                .kind(TransactionKind.REVERSAL)
                .channel(original.getChannel())
                .source(TransactionSource.WEB)
                .status(TransactionStatus.POSTED)
                .amountMinor(original.getAmountMinor())
                .currency(original.getCurrency())
                .description("Annulation de " + original.getReference() + " : " + reason.trim())
                .memberId(original.getMemberId())
                .createdBy(actor.getId())
                .reversesTransactionId(original.getId())
                .postedAt(now)
                .build();
        transactionRepository.save(reversal);

        writeEntries(reversal, reversalLegs);
        applyBalances(locked, reversalLegs);

        original.setStatus(TransactionStatus.REVERSED);
        original.setReversedAt(now);
        original.setReversalReason(reason.trim());
        transactionRepository.save(original);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalReference", original.getReference());
        metadata.put("reversalReference", reversal.getReference());
        metadata.put("amountMinor", original.getAmountMinor());
        metadata.put("reason", reason.trim());
        auditService.record(cooperative.getId(), actor.getId(), "TRANSACTION_REVERSED",
                "TRANSACTION", original.getId(),
                "Reversal %s of %s".formatted(reversal.getReference(), original.getReference()),
                metadata);

        log.warn("Reversed {} with {} by actor {}", original.getReference(),
                reversal.getReference(), actor.getId());
        return reversal;
    }

    private LedgerTransaction replay(LedgerTransaction existing, PostTransactionCommand command) {
        boolean consistent = existing.getKind() == command.kind()
                && existing.getAmountMinor() == command.amountMinor()
                && existing.getChannel() == command.channel()
                && java.util.Objects.equals(existing.getMemberId(), command.memberId())
                && java.util.Objects.equals(existing.getDescription().trim(), command.description().trim());
        if (!consistent) {
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "error.transaction.idempotency_conflict");
        }
        return existing;
    }

    private User resolveMember(PostTransactionCommand command, Cooperative cooperative) {
        if (command.kind().requiresMember() && command.memberId() == null) {
            throw ApiException.badRequest("MEMBER_REQUIRED", "error.ledger.member_required");
        }
        if (command.memberId() == null) {
            return null;
        }
        User member = userRepository.findById(command.memberId())
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "error.member.not_found"));
        if (!member.getCooperativeId().equals(cooperative.getId())) {
            throw ApiException.unprocessable("MEMBER_SCOPE", "error.ledger.member_mismatch");
        }
        return member;
    }

    private Account resolveChannelAccount(Cooperative cooperative, PaymentChannel channel) {
        return switch (channel) {
            case CASH -> systemAccount(cooperative, AccountCategory.CASH, null);
            case AIRTEL_MONEY -> systemAccount(cooperative, AccountCategory.MOBILE_MONEY, WalletProvider.AIRTEL);
            case ORANGE_MONEY -> systemAccount(cooperative, AccountCategory.MOBILE_MONEY, WalletProvider.ORANGE);
            case MPESA -> systemAccount(cooperative, AccountCategory.MOBILE_MONEY, WalletProvider.MPESA);
            default -> throw ApiException.unprocessable("UNSUPPORTED_CHANNEL", "error.ledger.unsupported_channel");
        };
    }

    private Account systemAccount(Cooperative cooperative, AccountCategory category, WalletProvider provider) {
        return (provider == null
                ? accountRepository.findByCooperativeIdAndCategoryAndMemberIdIsNull(
                        cooperative.getId(), category)
                : accountRepository.findByCooperativeIdAndCategoryAndWalletProviderAndMemberIdIsNull(
                        cooperative.getId(), category, provider))
                .orElseThrow(() -> ApiException.unprocessable("ACCOUNT_MISSING", "error.account.missing_wallet"));
    }

    private Account memberAccount(Cooperative cooperative, User member, AccountCategory category) {
        if (member == null) {
            throw ApiException.badRequest("MEMBER_REQUIRED", "error.ledger.member_required");
        }
        return accountRepository.findByCooperativeIdAndMemberIdAndCategory(
                        cooperative.getId(), member.getId(), category)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "error.account.not_found"));
    }

    /**
     * Locks the involved accounts in a deterministic UUID order. This is what
     * keeps concurrent deposits/withdrawals from deadlocking each other.
     */
    private Map<UUID, Account> lockInStableOrder(List<LedgerLeg> legs) {
        Set<UUID> ids = legs.stream().map(LedgerLeg::accountId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<UUID> ordered = ids.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        Map<UUID, Account> locked = new LinkedHashMap<>();
        for (UUID id : ordered) {
            Account account = accountRepository.lockById(id)
                    .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "error.account.not_found"));
            locked.put(account.getId(), account);
        }
        return locked;
    }

    private void assertAccountBelongsToCooperative(Account account, Cooperative cooperative) {
        if (!account.getCooperativeId().equals(cooperative.getId())) {
            throw ApiException.unprocessable("ACCOUNT_SCOPE", "error.ledger.account_scope");
        }
        if (!account.getCurrency().equals(cooperative.getCurrency())) {
            throw ApiException.unprocessable("CURRENCY_MISMATCH", "error.ledger.currency_mismatch");
        }
    }

    private void assertFundsAvailable(Account account, LedgerLeg leg) {
        if (account.getCategory() == AccountCategory.CASH
                || account.getCategory() == AccountCategory.MOBILE_MONEY) {
            if (leg.direction() == EntryDirection.CREDIT && account.getBalanceMinor() < leg.amountMinor()) {
                throw ApiException.unprocessable("INSUFFICIENT_CASH", "error.ledger.insufficient_cash");
            }
        }
        if (account.getCategory() == AccountCategory.SAVINGS
                || account.getCategory() == AccountCategory.SHARES) {
            if (leg.direction() == EntryDirection.DEBIT) {
                long available = -account.getBalanceMinor();
                if (available < leg.amountMinor()) {
                    throw ApiException.unprocessable("INSUFFICIENT_FUNDS", "error.ledger.insufficient_funds");
                }
            }
        }
    }

    private void writeEntries(LedgerTransaction transaction, List<LedgerLeg> legs) {
        List<LedgerEntry> entries = new ArrayList<>(legs.size());
        short line = 1;
        for (LedgerLeg leg : legs) {
            entries.add(LedgerEntry.builder()
                    .transactionId(transaction.getId())
                    .accountId(leg.accountId())
                    .direction(leg.direction())
                    .amountMinor(leg.amountMinor())
                    .lineNo(line++)
                    .build());
        }
        entryRepository.saveAll(entries);
    }

    private void applyBalances(Map<UUID, Account> locked, List<LedgerLeg> legs) {
        for (LedgerLeg leg : legs) {
            locked.get(leg.accountId()).apply(leg.direction(), leg.amountMinor());
        }
        accountRepository.saveAll(locked.values());
    }

    private static LedgerLeg debit(Account account, long amountMinor) {
        return new LedgerLeg(account.getId(), EntryDirection.DEBIT, amountMinor);
    }

    private static LedgerLeg credit(Account account, long amountMinor) {
        return new LedgerLeg(account.getId(), EntryDirection.CREDIT, amountMinor);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
