package com.insideinvoice.auth.repository;

import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Admin user list: search on name/email/username plus the "Active" toggle.
     * {@code activeOnly} narrows to users who generated an invoice since
     * {@code cutoff} via an EXISTS subquery that rides
     * {@code idx_invoices_created_by_created_at} (V35) instead of loading every
     * invoice to filter client-side. {@code q = ''} matches everyone.
     */
    @Query("SELECT u FROM User u WHERE (:q = '' "
            + "OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "AND (:activeOnly = false "
            + "OR EXISTS (SELECT i.id FROM Invoice i WHERE i.createdBy = u.id AND i.createdAt >= :cutoff))")
    Page<User> search(@Param("q") String q, @Param("activeOnly") boolean activeOnly,
            @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.email = :login OR u.username = :login")
    Optional<User> findByEmailOrUsername(@Param("login") String login);

    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findTop10ByOrderByCreatedAtDesc();

    /** Monthly platform signup counts since cutoff (row 0 = month timestamp). Admin new-users chart. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT DATE_TRUNC('month', created_at) AS m, COUNT(*) "
            + "FROM users WHERE created_at >= :cutoff GROUP BY 1 ORDER BY 1", nativeQuery = true)
    List<Object[]> monthlySignupCount(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Primary user record of a business (lowest id first is the account that
     * created/owns it). Used by the admin workspace to resolve "on behalf of"
     * ownership when the admin creates data inside a user's business.
     */
    List<User> findByBusinessIdOrderByIdAsc(Long businessId);

    long countByRole(Role role);

    long countByBusinessId(Long businessId);

    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);
}
