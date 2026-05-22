package com.insideinvoice.auth.repository;

import com.insideinvoice.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    boolean existsByEmail(String email);
}
