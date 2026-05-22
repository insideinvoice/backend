package com.insideinvoice.auth.repository;

import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    boolean existsByEmail(String email);

    List<User> findTop10ByOrderByCreatedAtDesc();

    long countByRole(Role role);

    long countByBusinessId(Long businessId);
}
