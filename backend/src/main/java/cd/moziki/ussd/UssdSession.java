package cd.moziki.ussd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Session state for the planned USSD channel. Purely groundwork for phase 2:
 * no USSD gateway is wired in yet, but the identity, locale and resumable
 * context (e.g. "deposit amount 5000 awaiting PIN") are modelled now.
 */
@Entity
@Table(name = "ussd_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UssdSession {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 16)
    private String phone;

    @Column(name = "cooperative_id")
    private UUID cooperativeId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 2)
    @Builder.Default
    private String locale = "fr";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private UssdState state = UssdState.IDLE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> context = Map.of();

    @Column(name = "last_activity_at", nullable = false)
    @Builder.Default
    private Instant lastActivityAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
