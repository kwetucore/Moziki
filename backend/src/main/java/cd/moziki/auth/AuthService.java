package cd.moziki.auth;

import cd.moziki.audit.AuditService;
import cd.moziki.auth.dto.LoginRequest;
import cd.moziki.auth.dto.LoginResponse;
import cd.moziki.auth.dto.MeResponse;
import cd.moziki.common.ApiException;
import cd.moziki.common.PhoneNumbers;
import cd.moziki.cooperative.Cooperative;
import cd.moziki.cooperative.CooperativeRepository;
import cd.moziki.cooperative.dto.CooperativeSummary;
import cd.moziki.security.JwtService;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import cd.moziki.user.dto.UserProfile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CooperativeRepository cooperativeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    /** Used to keep timing similar when the phone number does not exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       CooperativeRepository cooperativeRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.cooperativeRepository = cooperativeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("moziki-timing-equalizer");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        User user = userRepository.findByPhone(phone).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "error.auth.invalid_credentials");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "error.auth.invalid_credentials");
        }
        if (!user.isActive()) {
            throw ApiException.unauthorized("ACCOUNT_SUSPENDED", "error.auth.account_suspended");
        }

        Cooperative cooperative = cooperativeRepository.findById(user.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        UserPrincipal principal = UserPrincipal.from(user);
        String token = jwtService.issue(principal);

        auditService.record(user.getCooperativeId(), user.getId(), "USER_LOGIN", "USER", user.getId(),
                "Login from web app", Map.of("role", user.getRole().name()));

        return new LoginResponse(token, jwtService.getTtlMinutes() * 60,
                UserProfile.from(user), CooperativeSummary.from(cooperative));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "error.unauthenticated"));
        Cooperative cooperative = cooperativeRepository.findById(user.getCooperativeId())
                .orElseThrow(() -> ApiException.notFound("COOP_NOT_FOUND", "error.coop.not_found"));
        return new MeResponse(UserProfile.from(user), CooperativeSummary.from(cooperative));
    }
}
