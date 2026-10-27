package cd.moziki.reconciliation;

import cd.moziki.account.ChartOfAccountsService;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.ledger.LedgerService;
import cd.moziki.ledger.PaymentChannel;
import cd.moziki.ledger.PostTransactionCommand;
import cd.moziki.ledger.TransactionKind;
import cd.moziki.ledger.TransactionSource;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the reconciliation module against real PostgreSQL: Flyway V2,
 * append-only triggers, audit rows, RBAC and the member transparency summary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
@EnabledIfEnvironmentVariable(named = "MOZIKI_RUN_DB_TESTS", matches = "true",
        disabledReason = "Start PostgreSQL with ./scripts/dev-db.sh and run ./scripts/backend-test.sh")
class ReconciliationIntegrationTest {

    private static final String PASSWORD = "TestPass123!";
    private static final String TIMEZONE = "Africa/Kinshasa";

    @Autowired private ReconciliationService reconciliationService;
    @Autowired private CashReconciliationRepository reconciliationRepository;
    @Autowired private CooperativeRepository cooperativeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChartOfAccountsService chartOfAccountsService;
    @Autowired private LedgerService ledgerService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Cooperative cooperative;
    private User leader;
    private User auditor;
    private User member;
    private String leaderToken;
    private String auditorToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("""
                TRUNCATE TABLE reconciliation_reviews, reconciliation_evidence,
                                 cash_reconciliations, audit_logs, ledger_entries, transactions,
                                 accounts, users, cooperatives, ussd_sessions CASCADE
                """);

        cooperative = cooperativeRepository.save(Cooperative.builder()
                .code("REC")
                .name("Coopérative reconciliation")
                .currency("CDF")
                .defaultLocale("fr")
                .timezone(TIMEZONE)
                .build());
        chartOfAccountsService.provisionSystemAccounts(cooperative);

        leader = createUser("Leader Reconciliation", "+243990000201", Role.LEADER, "M-20001");
        auditor = createUser("Auditor Reconciliation", "+243990000202", Role.AUDITOR, "M-20002");
        member = createUser("Member Reconciliation", "+243990000203", Role.MEMBER, "M-20003");
        chartOfAccountsService.provisionMemberAccounts(cooperative, leader);
        chartOfAccountsService.provisionMemberAccounts(cooperative, auditor);
        chartOfAccountsService.provisionMemberAccounts(cooperative, member);

        // Seed 1,000 CDF of physical cash so the book balance is non-zero.
        ledgerService.post(new PostTransactionCommand(
                        TransactionKind.DEPOSIT, member.getId(), 1_000_00L,
                        PaymentChannel.CASH, "Opening cash for reconciliation", null,
                        "rec-seed", TransactionSource.WEB),
                UserPrincipal.from(leader));

        leaderToken = login(leader.getPhone());
        auditorToken = login(auditor.getPhone());
        memberToken = login(member.getPhone());
    }

    @Test
    void leaderSubmitsBalancedCountAndAuditTrailRecordsIt() throws Exception {
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.00", "0", "0", "0")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BALANCED"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.bookCashMinor").value(100_000))
                .andExpect(jsonPath("$.countedCashMinor").value(100_000))
                .andExpect(jsonPath("$.cashDifferenceMinor").value(0))
                .andExpect(jsonPath("$.reportedByName").value("Leader Reconciliation"))
                .andExpect(jsonPath("$.notes").value("Fin de journée"));

        assertThat(reconciliationRepository.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'CASH_RECONCILIATION_SUBMITTED'
                """, Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT ip_address FROM audit_logs WHERE action = 'CASH_RECONCILIATION_SUBMITTED'
                """, String.class)).isNotBlank();
    }

    @Test
    void offsettingDifferencesAcrossFundingSourcesAreFlagged() throws Exception {
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("900.00", "100.00", "0", "0")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISCREPANCY"))
                .andExpect(jsonPath("$.cashDifferenceMinor").value(-10_000))
                .andExpect(jsonPath("$.airtelDifferenceMinor").value(10_000))
                .andExpect(jsonPath("$.totalDifferenceMinor").value(0));
    }

    @Test
    void correctionCreatesPendingRevisionAndKeepsOriginal() throws Exception {
        String first = submit("1000.00", "0", "0", "0");
        assertThat(objectMapper.readTree(first).get("reviewStatus").asText())
                .isEqualTo("APPROVED");

        String body = mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("950.00", "0", "0", "0")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.status").value("DISCREPANCY"))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.supersedesId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode correction = objectMapper.readTree(body);
        assertThat(reconciliationRepository.count()).isEqualTo(2);

        CashReconciliation original = reconciliationRepository
                .findFirstByCooperativeIdAndBusinessDateOrderByRevisionDesc(
                        cooperative.getId(), today())
                .orElseThrow();
        assertThat(original.getRevision()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT counted_cash_minor FROM cash_reconciliations WHERE revision = 1
                """, Long.class)).isEqualTo(100_000L);
        assertThat(correction.get("supersedesId").asText()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'CASH_RECONCILIATION_CORRECTED'
                """, Long.class)).isEqualTo(1);

        // Members keep seeing the first, approved count until review.
        mockMvc.perform(get("/api/transparency/summary")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReviewCount").value(1))
                .andExpect(jsonPath("$.latestReconciliation.revision").value(1))
                .andExpect(jsonPath("$.latestReconciliation.totalDifferenceMinor").value(0));
    }

    @Test
    void auditorApprovalMakesCorrectionAuthoritative() throws Exception {
        submit("1000.00", "0", "0", "0");
        String correction = mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("950.00", "0", "0", "0")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reportId = objectMapper.readTree(correction).get("id").asText();

        mockMvc.perform(post("/api/reconciliations/{id}/review", reportId)
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVED",
                                "note", "Cash shortfall explained by the receipt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedByName").value("Auditor Reconciliation"))
                .andExpect(jsonPath("$.reviewNote").value("Cash shortfall explained by the receipt"));

        mockMvc.perform(get("/api/transparency/summary")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReviewCount").value(0))
                .andExpect(jsonPath("$.latestReconciliation.revision").value(2))
                .andExpect(jsonPath("$.latestReconciliation.totalDifferenceMinor").value(-5_000))
                .andExpect(jsonPath("$.latestReconciliation.reviewStatus").value("APPROVED"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'CASH_RECONCILIATION_APPROVED'
                """, Long.class)).isEqualTo(1);
    }

    @Test
    void rejectionRequiresReasonAndKeepsTheApprovedCount() throws Exception {
        submit("1000.00", "0", "0", "0");
        String reportId = objectMapper.readTree(
                        mockMvc.perform(post("/api/reconciliations")
                                        .header("Authorization", "Bearer " + leaderToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body("950.00", "0", "0", "0")))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/reconciliations/{id}/review", reportId)
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECTED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_REJECTION_NOTE_REQUIRED"));

        mockMvc.perform(post("/api/reconciliations/{id}/review", reportId)
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "REJECTED", "note", "Missing the bank slip"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));

        mockMvc.perform(get("/api/transparency/summary")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestReconciliation.revision").value(1))
                .andExpect(jsonPath("$.pendingReviewCount").value(0));

        // A rejected correction can be replaced by a new revision.
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("975.00", "0", "0", "0")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"));
    }

    @Test
    void anotherCorrectionIsBlockedWhileOneIsPending() throws Exception {
        submit("1000.00", "0", "0", "0");
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("950.00", "0", "0", "0")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("900.00", "0", "0", "0")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_PENDING_REVIEW"));
    }

    @Test
    void evidenceUploadIsValidatedStoredAndVisibleToMembers() throws Exception {
        String reportId = objectMapper.readTree(submit("1000.00", "0", "0", "0"))
                .get("id").asText();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile(
                "file", "cash-count.png", "image/png", png);

        String evidenceBody = mockMvc.perform(multipart("/api/reconciliations/{id}/evidence", reportId)
                        .file(file)
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("cash-count.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(png.length))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String evidenceId = objectMapper.readTree(evidenceBody).get("id").asText();

        mockMvc.perform(get("/api/reconciliations/{id}/evidence/{evidenceId}",
                        reportId, evidenceId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        mockMvc.perform(get("/api/reconciliations/{id}", reportId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[0].fileName").value("cash-count.png"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'RECONCILIATION_EVIDENCE_ADDED'
                """, Long.class)).isEqualTo(1);

        MockMultipartFile text = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/reconciliations/{id}/evidence", reportId)
                        .file(text)
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVIDENCE_TYPE_NOT_SUPPORTED"));

        MockMultipartFile disguised = new MockMultipartFile(
                "file", "fake.png", "image/png", "not a png".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/reconciliations/{id}/evidence", reportId)
                        .file(disguised)
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVIDENCE_CONTENT_MISMATCH"));

        mockMvc.perform(multipart("/api/reconciliations/{id}/evidence", reportId)
                        .file(file)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE reconciliation_evidence SET file_name = 'tampered'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void memberCanReadTransparencyButCannotSubmitOrReadRawHistory() throws Exception {
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("900.00", "0", "0", "0")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transparency/summary")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedToday").value(true))
                .andExpect(jsonPath("$.latestReconciliation.status").value("DISCREPANCY"))
                .andExpect(jsonPath("$.latestReconciliation.totalDifferenceMinor").value(-10_000))
                .andExpect(jsonPath("$.ledger.cashMinor").value(100_000))
                .andExpect(jsonPath("$.recentEvents").isArray())
                .andExpect(jsonPath("$.recentEvents[0].ipAddress").doesNotExist())
                .andExpect(jsonPath("$.recentEvents[0].metadata").doesNotExist());

        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("0", "0", "0", "0")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reconciliations")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reconciliations")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DISCREPANCY"));
    }

    @Test
    void futureBusinessDateIsRejected() throws Exception {
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today().plusDays(1), "0", "0", "0", "0")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_FUTURE_DATE"));
    }

    @Test
    void reportSnapshotAndAuditRowsAreImmutable() throws Exception {
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.00", "0", "0", "0")))
                .andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE cash_reconciliations SET counted_cash_minor = 0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM cash_reconciliations"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET summary = 'tampered'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void laterPostingsDoNotRewriteTheFrozenBookSnapshot() throws Exception {
        mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.00", "0", "0", "0")))
                .andExpect(status().isCreated());

        ledgerService.post(new PostTransactionCommand(
                        TransactionKind.DEPOSIT, member.getId(), 500_00L,
                        PaymentChannel.CASH, "Later deposit", null,
                        "rec-later", TransactionSource.WEB),
                UserPrincipal.from(leader));

        CashReconciliation report = reconciliationRepository.findAll().get(0);
        assertThat(report.getBookCashMinor()).isEqualTo(100_000L);
        assertThat(report.getCountedCashMinor()).isEqualTo(100_000L);
        assertThat(report.getStatus()).isEqualTo(ReconciliationStatus.BALANCED);
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

    private String login(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("phone", phone, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of(TIMEZONE));
    }

    private String submit(String cash, String airtel, String orange, String mpesa) throws Exception {
        return mockMvc.perform(post("/api/reconciliations")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(cash, airtel, orange, mpesa)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String body(String cash, String airtel, String orange, String mpesa) throws Exception {
        return body(today(), cash, airtel, orange, mpesa);
    }

    private String body(LocalDate date, String cash, String airtel, String orange, String mpesa)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "businessDate", date.toString(),
                "countedCash", cash,
                "countedAirtel", airtel,
                "countedOrange", orange,
                "countedMpesa", mpesa,
                "notes", "Fin de journée"));
    }
}
