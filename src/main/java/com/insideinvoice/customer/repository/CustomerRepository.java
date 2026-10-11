package com.insideinvoice.customer.repository;

import com.insideinvoice.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByBusinessId(Long businessId, Pageable pageable);

    java.util.List<Customer> findByIdInAndBusinessId(java.util.Collection<Long> ids, Long businessId);

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Customer> findByEmailAndBusinessId(String email, Long businessId);

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
