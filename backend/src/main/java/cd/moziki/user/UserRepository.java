package cd.moziki.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    List<User> findByCooperativeIdOrderByFullNameAsc(UUID cooperativeId);

    Page<User> findByCooperativeId(UUID cooperativeId, Pageable pageable);

    long countByCooperativeId(UUID cooperativeId);

    long countByCooperativeIdAndRole(UUID cooperativeId, Role role);
}
