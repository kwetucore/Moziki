package cd.moziki.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Written as a plain INSERT on purpose: the audit log is append-only, and
     * bypassing the entity lifecycle guarantees Hibernate can never emit an
     * UPDATE against it. The database trigger backs this up.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID cooperativeId, UUID actorId, String action, String entityType,
                       UUID entityId, String summary, Map<String, Object> metadata) {
        HttpServletRequest request = currentRequest();
        String json;
        try {
            json = objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Audit metadata is not serializable", ex);
        }

        jdbcTemplate.update("""
                INSERT INTO audit_logs
                    (cooperative_id, actor_id, action, entity_type, entity_id, summary,
                     metadata, ip_address, user_agent)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """,
                cooperativeId,
                actorId,
                action,
                entityType,
                entityId,
                truncate(summary, 500),
                json,
                request != null ? clientIp(request) : null,
                request != null ? truncate(request.getHeader("User-Agent"), 400) : null);

        log.info("audit coop={} actor={} action={} entity={}:{}", cooperativeId, actorId,
                action, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(UUID cooperativeId, Pageable pageable) {
        return repository.findByCooperativeId(cooperativeId, pageable);
    }

    public List<AuditLog> recent(UUID cooperativeId, Pageable pageable) {
        return repository.findByCooperativeId(cooperativeId, pageable).getContent();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
