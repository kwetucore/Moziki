package cd.moziki.cooperative;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "cooperatives")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cooperative {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 2)
    @Builder.Default
    private String country = "CD";

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "CDF";

    @Column(name = "default_locale", nullable = false, length = 2)
    @Builder.Default
    private String defaultLocale = "fr";

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Africa/Kinshasa";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
