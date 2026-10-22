package cd.moziki.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByCooperativeIdOrderByCodeAsc(UUID cooperativeId);

    List<Account> findByCooperativeIdAndMemberIdOrderByCodeAsc(UUID cooperativeId, UUID memberId);

    List<Account> findByCooperativeIdAndMemberIdNotNull(UUID cooperativeId);

    Optional<Account> findByIdAndCooperativeId(UUID id, UUID cooperativeId);

    Optional<Account> findByCooperativeIdAndCode(UUID cooperativeId, String code);

    Optional<Account> findByCooperativeIdAndCategoryAndMemberIdIsNull(UUID cooperativeId, AccountCategory category);

    Optional<Account> findByCooperativeIdAndCategoryAndWalletProviderAndMemberIdIsNull(
            UUID cooperativeId, AccountCategory category, WalletProvider walletProvider);

    Optional<Account> findByCooperativeIdAndMemberIdAndCategory(UUID cooperativeId, UUID memberId, AccountCategory category);

    /** Pessimistic row lock; the caller locks accounts in a stable id order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") UUID id);

    @Query("""
            select coalesce(sum(a.balanceMinor), 0)
            from Account a
            where a.cooperativeId = :cooperativeId and a.category = :category
            """)
    long sumBalanceByCategory(@Param("cooperativeId") UUID cooperativeId,
                              @Param("category") AccountCategory category);

    @Query("""
            select coalesce(sum(a.balanceMinor), 0)
            from Account a
            where a.cooperativeId = :cooperativeId
              and a.memberId = :memberId
              and a.category = :category
            """)
    long sumBalanceByMemberAndCategory(@Param("cooperativeId") UUID cooperativeId,
                                       @Param("memberId") UUID memberId,
                                       @Param("category") AccountCategory category);

    @Query(value = """
            SELECT a.id AS "accountId", a.code AS "code", a.name AS "name",
                   a.balance_minor AS "cachedMinor",
                   coalesce(v.ledger_balance_minor, 0) AS "ledgerMinor"
            FROM accounts a
            JOIN v_account_ledger_balances v ON v.account_id = a.id
            WHERE a.cooperative_id = :cooperativeId
            ORDER BY a.code
            """, nativeQuery = true)
    List<AccountReconciliationProjection> reconcile(@Param("cooperativeId") UUID cooperativeId);
}
