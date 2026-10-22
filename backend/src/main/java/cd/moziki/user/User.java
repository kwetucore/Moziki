package cd.moziki.user;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "cooperative_id", nullable = false)
    private UUID cooperativeId;

    @Column(name = "member_number", nullable = false, length = 24)
    private String memberNumber;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    /** E.164, e.g. +243990000001. Also the future USSD identity. */
    @Column(nullable = false, unique = true, length = 16)
    private String phone;

    @Column(unique = true, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, length = 2)
    @Builder.Default
    private String locale = "fr";

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
