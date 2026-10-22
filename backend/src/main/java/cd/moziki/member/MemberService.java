package cd.moziki.member;

import cd.moziki.account.Account;
import cd.moziki.account.AccountCategory;
import cd.moziki.account.AccountRepository;
import cd.moziki.account.ChartOfAccountsService;
import cd.moziki.audit.AuditService;
import cd.moziki.common.ApiException;
import cd.moziki.common.PhoneNumbers;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.ledger.ReferenceNumbers;
import cd.moziki.member.dto.CreateMemberRequest;
import cd.moziki.member.dto.MemberResponse;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.Role;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import cd.moziki.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemberService {

    private final UserRepository userRepository;
    private final CooperativeRepository cooperativeRepository;
    private final AccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final PasswordEncoder passwordEncoder;
    private final ReferenceNumbers referenceNumbers;
    private final AuditService auditService;

    public MemberService(UserRepository userRepository,
                         CooperativeRepository cooperativeRepository,
                         AccountRepository accountRepository,
                         ChartOfAccountsService chartOfAccountsService,
                         PasswordEncoder passwordEncoder,
                         ReferenceNumbers referenceNumbers,
                         AuditService auditService) {
        this.userRepository = userRepository;
        this.cooperativeRepository = cooperativeRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.passwordEncoder = passwordEncoder;
        this.referenceNumbers = referenceNumbers;
        this.auditService = auditService;
    }

    @Transactional
    public MemberResponse create(CreateMemberRequest request, UserPrincipal actor) {
        Cooperative cooperative = cooperativeRepository.findById(actor.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));

        String phone = PhoneNumbers.normalize(request.phone());
        if (userRepository.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_EXISTS", "error.member.phone_exists");
        }
        if (StringUtils.hasText(request.email()) && userRepository.existsByEmail(request.email().trim())) {
            throw ApiException.conflict("EMAIL_EXISTS", "error.member.email_exists");
        }

        Role role = request.role() == null ? Role.MEMBER : request.role();
        String locale = StringUtils.hasText(request.locale()) ? request.locale() : cooperative.getDefaultLocale();
        String memberNumber = referenceNumbers.nextMemberNumber();

        User member = userRepository.save(User.builder()
                .cooperativeId(cooperative.getId())
                .memberNumber(memberNumber)
                .fullName(request.fullName().trim())
                .phone(phone)
                .email(StringUtils.hasText(request.email()) ? request.email().trim().toLowerCase() : null)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .status(UserStatus.ACTIVE)
                .locale(locale)
                .build());

        chartOfAccountsService.provisionMemberAccounts(cooperative, member);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memberNumber", memberNumber);
        metadata.put("role", role.name());
        metadata.put("phone", PhoneNumbers.masked(phone));
        auditService.record(cooperative.getId(), actor.getId(), "MEMBER_CREATED", "USER", member.getId(),
                "Member %s (%s) onboarded as %s".formatted(member.getFullName(), memberNumber, role), metadata);

        return toResponse(member, Map.of());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(UserPrincipal principal) {
        List<User> users = userRepository.findByCooperativeIdOrderByFullNameAsc(principal.getCooperativeId());
        Map<UUID, Map<AccountCategory, Long>> balances = loadBalances(principal.getCooperativeId());

        List<MemberResponse> result = new ArrayList<>(users.size());
        for (User user : users) {
            result.add(toResponse(user, balances.getOrDefault(user.getId(), Map.of())));
        }
        return result;
    }

    private Map<UUID, Map<AccountCategory, Long>> loadBalances(UUID cooperativeId) {
        Map<UUID, Map<AccountCategory, Long>> grouped = new LinkedHashMap<>();
        for (Account account : accountRepository.findByCooperativeIdAndMemberIdNotNull(cooperativeId)) {
            if (account.getMemberId() == null) {
                continue;
            }
            grouped.computeIfAbsent(account.getMemberId(), key -> new LinkedHashMap<>())
                    .put(account.getCategory(), account.displayBalanceMinor());
        }
        return grouped;
    }

    private MemberResponse toResponse(User member, Map<AccountCategory, Long> balances) {
        return new MemberResponse(
                member.getId(),
                member.getMemberNumber(),
                member.getFullName(),
                member.getPhone(),
                member.getEmail(),
                member.getRole(),
                member.getStatus(),
                member.getLocale(),
                balances.getOrDefault(AccountCategory.SAVINGS, 0L),
                balances.getOrDefault(AccountCategory.SHARES, 0L),
                member.getCreatedAt()
        );
    }
}
