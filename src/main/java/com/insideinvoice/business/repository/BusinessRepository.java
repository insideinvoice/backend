package com.insideinvoice.business.repository;

import com.insideinvoice.business.entity.Business;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Business b WHERE b.id = :id")
    Optional<Business> findByIdWithLock(@Param("id") Long id);

    /** Admin list search on business/owner/email/GSTIN/phone (trigram-indexed, V35). */
    @Query("SELECT b FROM Business b WHERE (:q = '' "
            + "OR LOWER(b.businessName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(b.ownerName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(b.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(b.gstIn) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR b.phone LIKE CONCAT('%', :q, '%'))")
    Page<Business> searchAll(@Param("q") String q, Pageable pageable);
}
