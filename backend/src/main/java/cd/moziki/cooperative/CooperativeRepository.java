package cd.moziki.cooperative;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CooperativeRepository extends JpaRepository<Cooperative, UUID> {

    Optional<Cooperative> findByCode(String code);

    boolean existsByCode(String code);
}
