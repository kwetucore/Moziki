package cd.moziki.ussd;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UssdSessionRepository extends JpaRepository<UssdSession, UUID> {

    Optional<UssdSession> findFirstByPhoneAndExpiresAtAfterOrderByLastActivityAtDesc(String phone, Instant now);

    long deleteByExpiresAtBefore(Instant now);
}
