package cd.moziki.audit;

import cd.moziki.audit.dto.AuditLogResponse;
import cd.moziki.common.PageResponse;
import cd.moziki.security.UserPrincipal;
import cd.moziki.user.User;
import cd.moziki.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final UserRepository userRepository;

    public AuditController(AuditService auditService, UserRepository userRepository) {
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEADER','AUDITOR')")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<AuditLog> logs = auditService.list(principal.getCooperativeId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        Set<UUID> actorIds = new HashSet<>();
        logs.getContent().forEach(log -> {
            if (log.getActorId() != null) {
                actorIds.add(log.getActorId());
            }
        });
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(actorIds).forEach(user -> names.put(user.getId(), user.getFullName()));

        List<AuditLogResponse> content = logs.getContent().stream()
                .map(log -> new AuditLogResponse(log.getId(), log.getActorId(),
                        log.getActorId() == null ? null : names.get(log.getActorId()),
                        log.getAction(), log.getEntityType(), log.getEntityId(), log.getSummary(),
                        log.getMetadata(), log.getIpAddress(), log.getCreatedAt()))
                .toList();

        return PageResponse.of(content, logs.getNumber(), logs.getSize(),
                logs.getTotalElements(), logs.getTotalPages());
    }
}
