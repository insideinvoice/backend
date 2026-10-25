package com.insideinvoice.customer.repository;

import com.insideinvoice.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByBusinessId(Long businessId, Pageable pageable);

    /**
     * Cross-tenant admin search (name / email / phone / GSTIN) + per-business
     * workspace search. {@code q = ''} matches everything, so one query serves
     * both list and filter paths; matches ride the trigram indexes (V35).
     */
    @Query("SELECT c FROM Customer c WHERE (:q = '' "
            + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR c.phone LIKE CONCAT('%', :q, '%') "
            + "OR LOWER(c.gstIn) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Customer> searchAll(@Param("q") String q, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.businessId = :businessId AND (:q = '' "
            + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR c.phone LIKE CONCAT('%', :q, '%') "
            + "OR LOWER(c.gstIn) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Customer> searchByBusinessId(@Param("businessId") Long businessId, @Param("q") String q, Pageable pageable);

    java.util.List<Customer> findByIdInAndBusinessId(java.util.Collection<Long> ids, Long businessId);

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Customer> findByEmailAndBusinessId(String email, Long businessId);

    /** Case-insensitive so "Acme@x.com" and "acme@x.com" are the same customer. */
    Optional<Customer> findByEmailIgnoreCaseAndBusinessId(String email, Long businessId);

    Optional<Customer> findByPhoneAndBusinessId(String phone, Long businessId);

    /**
     * Bulk import matches rows to existing customers by GSTIN first (most stable
     * identifier), then by name. Case-insensitive so "Acme Corp" and "ACME CORP"
     * resolve to the same customer. Uses an explicit @Query because Spring Data
     * would otherwise parse "GstInIgnoreCase" as property gst + the In keyword.
     */
    @org.springframework.data.jpa.repository.Query(
            "select c from Customer c where lower(c.gstIn) = lower(:gstIn) and c.businessId = :businessId")
    Optional<Customer> findByGstInIgnoreCaseAndBusinessId(@org.springframework.data.repository.query.Param("gstIn") String gstIn,
                                                         @org.springframework.data.repository.query.Param("businessId") Long businessId);

    @org.springframework.data.jpa.repository.Query(
            "select c from Customer c where lower(c.name) = lower(:name) and c.businessId = :businessId")
    java.util.List<Customer> findByNameIgnoreCaseAndBusinessId(@org.springframework.data.repository.query.Param("name") String name,
                                                               @org.springframework.data.repository.query.Param("businessId") Long businessId);

    boolean existsByEmailAndBusinessId(String email, Long businessId);

    boolean existsByPhoneAndBusinessId(String phone, Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    void deleteByBusinessId(Long businessId);
}
