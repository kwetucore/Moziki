package cd.moziki.ledger;

import cd.moziki.account.Account;
import cd.moziki.account.AccountRepository;
import cd.moziki.account.ChartOfAccountsService;
import cd.moziki.common.ApiException;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.security.UserPrincipal;
import cd.moziki.support.TestDatabaseConfig;
import cd.moziki.user.Role;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against a real PostgreSQL database (moziki_test) so the Flyway
 * migration, deferred double-entry constraint trigger, immutability guards and
 * JWT/RBAC stack are all exercised for real.
 *
 * Usage: ./scripts/backend-test.sh
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
@EnabledIfEnvironmentVariable(named = "MOZIKI_RUN_DB_TESTS", matches = "true",
        disabledReason = "Start PostgreSQL with ./scripts/dev-db.sh and run ./scripts/backend-test.sh")
class LedgerIntegrationTest {

    private static final long TEN_THOUSAND_CDF = 1_000_000L;
    private static final String PASSWORD = "TestPass123!";

    @Autowired private LedgerService ledgerService;
    @Autowired private LedgerTransactionRepository transactionRepository;
    @Autowired private LedgerEntryRepository entryRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CooperativeRepository cooperativeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChartOfAccountsService chartOfAccountsService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private Cooperative cooperative;
    private User leader;
    private User auditor;
    private User member;
    private UserPrincipal leaderPrincipal;
    private Account cash;
    private Account savings;
    private Account shares;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE audit_logs, ledger_entries, transactions, accounts,
                                 users, cooperatives, ussd_sessions CASCADE
                """);

        cooperative = cooperativeRepository.save(Cooperative.builder()
                .code("TEST")
                .name("Coopérative de test")
                .currency("CDF")
                .defaultLocale("fr")
                .build());
        chartOfAccountsService.provisionSystemAccounts(cooperative);

        leader = createUser("Leader Test", "+243990000101", Role.LEADER, "M-10001");
        auditor = createUser("Auditor Test", "+243990000102", Role.AUDITOR, "M-10002");
        member = createUser("Member Test", "+243990000103", Role.MEMBER, "M-10003");
        chartOfAccountsService.provisionMemberAccounts(cooperative, leader);
        chartOfAccountsService.provisionMemberAccounts(cooperative, auditor);
        chartOfAccountsService.provisionMemberAccounts(cooperative, member);

        leaderPrincipal = UserPrincipal.from(leader);
        cash = accountRepository.findByCooperativeIdAndCode(cooperative.getId(), "1000").orElseThrow();
        savings = accountRepository.findByCooperativeIdAndMemberIdAndCategory(
                cooperative.getId(), member.getId(), cd.moziki.account.AccountCategory.SAVINGS).orElseThrow();
        shares = accountRepository.findByCooperativeIdAndMemberIdAndCategory(
                cooperative.getId(), member.getId(), cd.moziki.account.AccountCategory.SHARES).orElseThrow();
    }

    @Test
    void postsBalancedDepositAndMaintainsCachedBalances() {
        LedgerTransaction transaction = postDeposit(member.getId(), TEN_THOUSAND_CDF, "test-deposit-1");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(transaction.getReference()).startsWith("MOZ-");
        assertThat(transaction.getCurrency()).isEqualTo("CDF");

        List<LedgerEntry> entries = entryRepository.findByTransactionIdOrderByLineNoAsc(transaction.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(LedgerEntry::getDirection)
                .containsExactlyInAnyOrder(EntryDirection.DEBIT, EntryDirection.CREDIT);
        long debits = entries.stream().filter(e -> e.getDirection() == EntryDirection.DEBIT)
                .mapToLong(LedgerEntry::getAmountMinor).sum();
        long credits = entries.stream().filter(e -> e.getDirection() == EntryDirection.CREDIT)
                .mapToLong(LedgerEntry::getAmountMinor).sum();
        assertThat(debits).isEqualTo(credits).isEqualTo(TEN_THOUSAND_CDF);

        assertThat(accountRepository.findById(cash.getId()).orElseThrow().getBalanceMinor())
                .isEqualTo(TEN_THOUSAND_CDF);
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalanceMinor())
                .isEqualTo(-TEN_THOUSAND_CDF);
        assertThat(accountRepository.reconcile(cooperative.getId()))
                .allMatch(row -> row.getCachedMinor() == row.getLedgerMinor());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'TRANSACTION_POSTED'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void databaseRejectsUnbalancedLedgerEntriesEvenWhenServiceIsBypassed() throws Exception {
        UUID transactionId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement header = connection.prepareStatement("""
                    INSERT INTO transactions
                        (id, cooperative_id, reference, kind, channel, amount_minor, currency,
                         description, member_id, created_by)
                    VALUES (?, ?, ?, 'DEPOSIT', 'CASH', 1000, 'CDF', 'malicious insert', ?, ?)
                    """)) {
                header.setObject(1, transactionId);
                header.setObject(2, cooperative.getId());
                header.setString(3, "BYPASS-" + transactionId.toString().substring(0, 8));
                header.setObject(4, member.getId());
                header.setObject(5, leader.getId());
                header.executeUpdate();
            }
            try (PreparedStatement line = connection.prepareStatement("""
                    INSERT INTO ledger_entries (transaction_id, account_id, direction, amount_minor, line_no)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                line.setObject(1, transactionId);
                line.setObject(2, cash.getId());
                line.setString(3, "DEBIT");
                line.setLong(4, 1000);
                line.setInt(5, 1);
                line.executeUpdate();

                line.setObject(1, transactionId);
                line.setObject(2, savings.getId());
                line.setString(3, "CREDIT");
                line.setLong(4, 900);
                line.setInt(5, 2);
                line.executeUpdate();
            }

            assertThatThrownBy(connection::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("unbalanced transaction");
            connection.rollback();
        }
    }

    @Test
    void ledgerEntriesCannotBeUpdatedOrDeleted() {
        LedgerTransaction transaction = postDeposit(member.getId(), 5_000_00L, "test-immutable");
        LedgerEntry entry = entryRepository.findByTransactionIdOrderByLineNoAsc(transaction.getId()).get(0);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update ledger_entries set amount_minor = 1 where id = ?", entry.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from ledger_entries where id = ?", entry.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void idempotencyKeyPreventsDoublePosting() {
        PostTransactionCommand command = depositCommand(member.getId(), TEN_THOUSAND_CDF, "offline-retry-42");

        LedgerTransaction first = ledgerService.post(command, leaderPrincipal);
        LedgerTransaction second = ledgerService.post(command, leaderPrincipal);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalanceMinor())
                .isEqualTo(-TEN_THOUSAND_CDF);
    }

    @Test
    void rejectsWithdrawalBeyondSavingsBalance() {
        postDeposit(member.getId(), TEN_THOUSAND_CDF, "withdrawal-seed");

        assertThatThrownBy(() -> ledgerService.post(
                new PostTransactionCommand(TransactionKind.WITHDRAWAL, member.getId(),
                        TEN_THOUSAND_CDF + 1, PaymentChannel.CASH, "Trop de retrait",
                        null, "over-withdrawal", TransactionSource.WEB), leaderPrincipal))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalanceMinor())
                .isEqualTo(-TEN_THOUSAND_CDF);
        assertThat(accountRepository.findById(cash.getId()).orElseThrow().getBalanceMinor())
                .isEqualTo(TEN_THOUSAND_CDF);
    }

    @Test
    void reversalRestoresBalancesAndKeepsOriginalHistory() {
        LedgerTransaction original = postDeposit(member.getId(), TEN_THOUSAND_CDF, "reversal-seed");

        LedgerTransaction reversal = ledgerService.reverse(original.getId(),
                "Erreur de saisie", leaderPrincipal);

        assertThat(reversal.getKind()).isEqualTo(TransactionKind.REVERSAL);
        assertThat(reversal.getReversesTransactionId()).isEqualTo(original.getId());
        assertThat(transactionRepository.findById(original.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.REVERSED);
        assertThat(transactionRepository.findById(original.getId()).orElseThrow().getReversalReason())
                .isEqualTo("Erreur de saisie");
        assertThat(accountRepository.findById(cash.getId()).orElseThrow().getBalanceMinor()).isZero();
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalanceMinor()).isZero();
        assertThat(entryRepository.findByTransactionIdOrderByLineNoAsc(original.getId())).hasSize(2);
        assertThat(entryRepository.findByTransactionIdOrderByLineNoAsc(reversal.getId())).hasSize(2);

        assertThatThrownBy(() -> ledgerService.reverse(original.getId(),
                "Encore", leaderPrincipal))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("ALREADY_REVERSED");
    }

    @Test
    void memberCannotPostButCanReadOwnLedger() throws Exception {
        postDeposit(member.getId(), TEN_THOUSAND_CDF, "rbac-seed");
        postDeposit(auditor.getId(), 2_000_00L, "rbac-other");

        String memberToken = login("+243990000103");
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"DEPOSIT","memberId":"%s","amount":1000,
                                 "channel":"CASH","description":"unauthorized"}
                                """.formatted(member.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        String auditorToken = login("+243990000102");
        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"DEPOSIT","memberId":"%s","amount":1000,
                                 "channel":"CASH","description":"auditor attempt"}
                                """.formatted(member.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderPostsThroughHttpAndAuditTrailIsVisible() throws Exception {
        String token = login("+243990000101");

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"DEPOSIT","memberId":"%s","amount":25000.50,
                                 "channel":"AIRTEL_MONEY","description":"Dépôt test HTTP",
                                 "idempotencyKey":"http-test-1"}
                                """.formatted(member.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("MOZ-")))
                .andExpect(jsonPath("$.amountMinor").value(2_500_050))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.memberName").value("Member Test"));

        mockMvc.perform(get("/api/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("TRANSACTION_POSTED"));

        mockMvc.perform(get("/api/accounts/reconciliation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].consistent").value(true));
    }

    @Test
    void apiErrorsAreLocalizedInEnglishFrenchAndSwahili() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+243990000103\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Numéro de téléphone ou mot de passe incorrect."));

        mockMvc.perform(post("/api/auth/login")
                        .header("Accept-Language", "sw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+243990000103\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Namba ya simu au neno la siri si sahihi."));

        mockMvc.perform(post("/api/auth/login")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+243990000103\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect phone number or password."));
    }

    @Test
    void sharePurchaseCreditsMemberEquity() {
        LedgerTransaction transaction = ledgerService.post(new PostTransactionCommand(
                        TransactionKind.SHARE_PURCHASE, member.getId(), 3_000_00L,
                        PaymentChannel.CASH, "Achat de parts", null, "shares-1",
                        TransactionSource.WEB),
                leaderPrincipal);

        assertThat(transaction.getKind()).isEqualTo(TransactionKind.SHARE_PURCHASE);
        assertThat(accountRepository.findById(shares.getId()).orElseThrow().displayBalanceMinor())
                .isEqualTo(3_000_00L);
        assertThat(accountRepository.findById(cash.getId()).orElseThrow().getBalanceMinor())
                .isEqualTo(3_000_00L);
    }

    private User createUser(String name, String phone, Role role, String memberNumber) {
        return userRepository.save(User.builder()
                .cooperativeId(cooperative.getId())
                .memberNumber(memberNumber)
                .fullName(name)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(role)
                .locale("fr")
                .build());
    }

    private PostTransactionCommand depositCommand(UUID memberId, long amountMinor, String key) {
        return new PostTransactionCommand(TransactionKind.DEPOSIT, memberId, amountMinor,
                PaymentChannel.CASH, "Dépôt de test", null, key, TransactionSource.WEB);
    }

    private LedgerTransaction postDeposit(UUID memberId, long amountMinor, String key) {
        return ledgerService.post(depositCommand(memberId, amountMinor, key), leaderPrincipal);
    }

    private String login(String phone) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("phone", phone, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.get("token").asText();
    }
}
