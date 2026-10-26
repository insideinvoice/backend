package com.insideinvoice.auth.repository;

import com.insideinvoice.auth.entity.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {
    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);
    List<PersonalAccessToken> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<PersonalAccessToken> findByBusinessIdOrderByCreatedAtDesc(Long businessId);
}
