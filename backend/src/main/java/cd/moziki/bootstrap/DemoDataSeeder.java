package cd.moziki.bootstrap;

import cd.moziki.account.ChartOfAccountsService;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.ledger.LedgerService;
import cd.moziki.ledger.PaymentChannel;
import cd.moziki.ledger.PostTransactionCommand;
import cd.moziki.ledger.ReferenceNumbers;
import cd.moziki.ledger.TransactionKind;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.Role;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import cd.moziki.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Development-only seed data. Never enabled in production (see application-prod).
 * Demo transactions are posted through the real ledger service, so the seeded
 * balances are backed by genuine double-entry records.
 */
@Component
@ConditionalOnProperty(prefix = "moziki.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CooperativeRepository cooperativeRepository;
    private final UserRepository userRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final LedgerService ledgerService;
    private final ReferenceNumbers referenceNumbers;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(CooperativeRepository cooperativeRepository,
                          UserRepository userRepository,
                          ChartOfAccountsService chartOfAccountsService,
                          LedgerService ledgerService,
                          ReferenceNumbers referenceNumbers,
                          PasswordEncoder passwordEncoder,
                          @Value("${moziki.demo-data.password}") String demoPassword) {
        this.cooperativeRepository = cooperativeRepository;
        this.userRepository = userRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.ledgerService = ledgerService;
        this.referenceNumbers = referenceNumbers;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (cooperativeRepository.existsByCode("UMOJA")) {
            log.info("Demo cooperative already present; skipping seed.");
            return;
        }

        Cooperative cooperative = cooperativeRepository.save(Cooperative.builder()
                .code("UMOJA")
                .name("Coopérative Umoja – Bukavu")
                .country("CD")
                .currency("CDF")
                .defaultLocale("fr")
                .timezone("Africa/Kinshasa")
                .build());
        chartOfAccountsService.provisionSystemAccounts(cooperative);

        User leader = createUser(cooperative, "Aline Mwamba", "+243990000001",
                "aline@moziki.cd", Role.LEADER, "fr");
        User auditor = createUser(cooperative, "Jean Kabasele", "+243990000002",
                "jean@moziki.cd", Role.AUDITOR, "fr");
        User member = createUser(cooperative, "Espérance Nabintu", "+243990000003",
                "esperance@moziki.cd", Role.MEMBER, "sw");
        User member2 = createUser(cooperative, "Patrick Bahati", "+243990000004",
                "patrick@moziki.cd", Role.MEMBER, "fr");

        for (User user : List.of(leader, auditor, member, member2)) {
            chartOfAccountsService.provisionMemberAccounts(cooperative, user);
        }

        UserPrincipal actor = UserPrincipal.from(leader);
        post(actor, TransactionKind.DEPOSIT, member.getId(), 50_000_00L, PaymentChannel.CASH,
                "Dépôt d'épargne initial", "demo-1");
        post(actor, TransactionKind.DEPOSIT, member2.getId(), 35_000_00L, PaymentChannel.AIRTEL_MONEY,
                "Dépôt via Airtel Money", "demo-2");
        post(actor, TransactionKind.SHARE_PURCHASE, member.getId(), 10_000_00L, PaymentChannel.CASH,
                "Achat de parts sociales", "demo-3");
        post(actor, TransactionKind.DEPOSIT, member2.getId(), 20_000_00L, PaymentChannel.ORANGE_MONEY,
                "Dépôt via Orange Money", "demo-4");
        post(actor, TransactionKind.LOAN_DISBURSEMENT, member.getId(), 30_000_00L, PaymentChannel.CASH,
                "Prêt accordé à Espérance", "demo-5");
        post(actor, TransactionKind.LOAN_REPAYMENT, member.getId(), 5_000_00L, PaymentChannel.AIRTEL_MONEY,
                "Remboursement partiel du prêt", "demo-6");
        post(actor, TransactionKind.FEE, member2.getId(), 500_00L, PaymentChannel.CASH,
                "Frais d'adhésion", "demo-7");

        log.info("Seeded demo cooperative UMOJA with {} members and 7 posted transactions. "
                        + "Demo accounts are documented in README.md.",
                userRepository.countByCooperativeId(cooperative.getId()));
    }

    private User createUser(Cooperative cooperative, String fullName, String phone, String email,
                            Role role, String locale) {
        String memberNumber = referenceNumbers.nextMemberNumber();
        return userRepository.save(User.builder()
                .cooperativeId(cooperative.getId())
                .memberNumber(memberNumber)
                .fullName(fullName)
                .phone(phone)
                .email(email)
                .passwordHash(passwordEncoder.encode(demoPassword))
                .role(role)
                .status(UserStatus.ACTIVE)
                .locale(locale)
                .build());
    }

    private void post(UserPrincipal actor, TransactionKind kind, java.util.UUID memberId,
                      long amountMinor, PaymentChannel channel, String description, String demoKey) {
        ledgerService.post(new PostTransactionCommand(kind, memberId, amountMinor, channel,
                description, null, demoKey, cd.moziki.ledger.TransactionSource.SYSTEM), actor);
    }
}
