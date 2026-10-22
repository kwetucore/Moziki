package cd.moziki.account;

import cd.moziki.cooperative.Cooperative;
import cd.moziki.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Standard chart of accounts for a community cooperative. Codes are stable so
 * reports, imports and (future) USSD flows can rely on them.
 */
@Service
public class ChartOfAccountsService {

    public static final String CASH = "1000";
    public static final String AIRTEL_MONEY = "1010";
    public static final String ORANGE_MONEY = "1020";
    public static final String MPESA = "1030";
    public static final String LOANS_RECEIVABLE = "1100";
    public static final String FEE_INCOME = "4000";
    public static final String INTEREST_INCOME = "4100";
    public static final String MEMBER_SAVINGS_PREFIX = "2000-";
    public static final String MEMBER_SHARES_PREFIX = "3000-";

    private final AccountRepository accountRepository;

    public ChartOfAccountsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void provisionSystemAccounts(Cooperative cooperative) {
        createSystemIfMissing(cooperative, CASH, "Caisse (espèces)",
                AccountType.ASSET, AccountCategory.CASH, null);
        createSystemIfMissing(cooperative, AIRTEL_MONEY, "Airtel Money",
                AccountType.ASSET, AccountCategory.MOBILE_MONEY, WalletProvider.AIRTEL);
        createSystemIfMissing(cooperative, ORANGE_MONEY, "Orange Money",
                AccountType.ASSET, AccountCategory.MOBILE_MONEY, WalletProvider.ORANGE);
        createSystemIfMissing(cooperative, MPESA, "M-Pesa",
                AccountType.ASSET, AccountCategory.MOBILE_MONEY, WalletProvider.MPESA);
        createSystemIfMissing(cooperative, LOANS_RECEIVABLE, "Prêts accordés",
                AccountType.ASSET, AccountCategory.LOANS, null);
        createSystemIfMissing(cooperative, FEE_INCOME, "Produits – frais",
                AccountType.INCOME, AccountCategory.FEES, null);
        createSystemIfMissing(cooperative, INTEREST_INCOME, "Produits – intérêts",
                AccountType.INCOME, AccountCategory.INTEREST, null);
    }

    @Transactional
    public void provisionMemberAccounts(Cooperative cooperative, User member) {
        createMemberIfMissing(cooperative, member, MEMBER_SAVINGS_PREFIX + member.getMemberNumber(),
                "Épargne – " + member.getFullName(), AccountType.LIABILITY, AccountCategory.SAVINGS);
        createMemberIfMissing(cooperative, member, MEMBER_SHARES_PREFIX + member.getMemberNumber(),
                "Parts sociales – " + member.getFullName(), AccountType.EQUITY, AccountCategory.SHARES);
    }

    private void createSystemIfMissing(Cooperative cooperative, String code, String name,
                                       AccountType type, AccountCategory category,
                                       WalletProvider provider) {
        if (accountRepository.findByCooperativeIdAndCode(cooperative.getId(), code).isPresent()) {
            return;
        }
        accountRepository.save(Account.builder()
                .cooperativeId(cooperative.getId())
                .code(code)
                .name(name)
                .type(type)
                .category(category)
                .walletProvider(provider)
                .currency(cooperative.getCurrency())
                .systemAccount(true)
                .build());
    }

    private void createMemberIfMissing(Cooperative cooperative, User member, String code, String name,
                                       AccountType type, AccountCategory category) {
        if (accountRepository.findByCooperativeIdAndCode(cooperative.getId(), code).isPresent()) {
            return;
        }
        accountRepository.save(Account.builder()
                .cooperativeId(cooperative.getId())
                .memberId(member.getId())
                .code(code)
                .name(name)
                .type(type)
                .category(category)
                .currency(cooperative.getCurrency())
                .systemAccount(false)
                .build());
    }
}
