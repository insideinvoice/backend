package com.insideinvoice.business.repository;

import com.insideinvoice.business.entity.Business;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Business b WHERE b.id = :id")
    Optional<Business> findByIdWithLock(@Param("id") Long id);
}
