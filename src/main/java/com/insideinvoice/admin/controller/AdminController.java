package com.insideinvoice.admin.controller;

import com.insideinvoice.admin.dto.AdminStatsResponse;
import com.insideinvoice.admin.dto.UpdatePasswordRequest;
import com.insideinvoice.admin.dto.UserWithPasswordResponse;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.dto.response.BusinessResponse;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.mapper.BusinessMapper;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.customer.dto.response.CustomerResponse;
import com.insideinvoice.customer.mapper.CustomerMapper;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.dto.request.UpdateInvoiceRequest;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.mapper.InvoiceMapper;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.service.InvoiceService;
import com.insideinvoice.product.dto.response.ProductResponse;
import com.insideinvoice.product.entity.Product;
import com.insideinvoice.product.mapper.ProductMapper;
import com.insideinvoice.product.repository.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin management APIs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessMapper businessMapper;
    private final InvoiceMapper invoiceMapper;
    @jakarta.persistence.PersistenceContext
    private EntityManager entityManager;
    private final InvoiceService invoiceService;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;

    @GetMapping("/users")
    @Operation(summary = "Get all users with passwords (Admin only)")
    public ResponseEntity<ApiResponse<List<UserWithPasswordResponse>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserWithPasswordResponse> result = users.stream()
                .map(u -> UserWithPasswordResponse.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .password(u.getPassword())
                        .rawPassword(u.getRawPassword())
                        .businessId(u.getBusinessId())
                        .createdAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", result));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get admin dashboard statistics")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        long totalUsers = userRepository.count();
        long totalBusinesses = businessRepository.count();
        long totalInvoices = invoiceRepository.count();
        long totalCustomers = customerRepository.count();
        long totalProducts = productRepository.count();

        List<User> recentUsersList = userRepository.findTop10ByOrderByCreatedAtDesc();
        List<AdminStatsResponse.UserSummary> recentUsers = recentUsersList.stream()
                .map(u -> AdminStatsResponse.UserSummary.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .businessId(u.getBusinessId())
                        .createdAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
                        .build())
                .toList();

        AdminStatsResponse stats = AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalBusinesses(totalBusinesses)
                .totalInvoices(totalInvoices)
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .recentUsers(recentUsers)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Admin stats retrieved", stats));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Get monthly growth analytics (Admin only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Monthly user signups
        Query userQuery = entityManager.createNativeQuery(
            "SELECT TO_CHAR(date_trunc('month', created_at), 'YYYY-MM') AS month, COUNT(*) AS count " +
            "FROM users GROUP BY date_trunc('month', created_at) ORDER BY month");
        List<Object[]> userRows = userQuery.getResultList();
        List<Map<String, Object>> usersByMonth = userRows.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("month", r[0]);
            m.put("count", r[1]);
            return m;
        }).toList();

        // Monthly invoices created
        Query invQuery = entityManager.createNativeQuery(
            "SELECT TO_CHAR(date_trunc('month', created_at), 'YYYY-MM') AS month, COUNT(*) AS count " +
            "FROM invoices GROUP BY date_trunc('month', created_at) ORDER BY month");
        List<Map<String, Object>> invoicesByMonth = ((List<Object[]>) invQuery.getResultList()).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("month", r[0]);
            m.put("count", r[1]);
            return m;
        }).toList();

        // Monthly revenue = count of sales (invoices) per month
        Query revQuery = entityManager.createNativeQuery(
            "SELECT TO_CHAR(date_trunc('month', created_at), 'YYYY-MM') AS month, COUNT(*) AS revenue " +
            "FROM invoices WHERE status NOT IN ('DRAFT', 'CANCELLED') GROUP BY date_trunc('month', created_at) ORDER BY month");
        List<Map<String, Object>> revenueByMonth = ((List<Object[]>) revQuery.getResultList()).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("month", r[0]);
            m.put("revenue", r[1]);
            return m;
        }).toList();

        // Monthly customer additions
        Query custQuery = entityManager.createNativeQuery(
            "SELECT TO_CHAR(date_trunc('month', created_at), 'YYYY-MM') AS month, COUNT(*) AS count " +
            "FROM customers GROUP BY date_trunc('month', created_at) ORDER BY month");
        List<Map<String, Object>> customersByMonth = ((List<Object[]>) custQuery.getResultList()).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("month", r[0]);
            m.put("count", r[1]);
            return m;
        }).toList();

        // Monthly business registrations
        Query bizQuery = entityManager.createNativeQuery(
            "SELECT TO_CHAR(date_trunc('month', created_at), 'YYYY-MM') AS month, COUNT(*) AS count " +
            "FROM businesses GROUP BY date_trunc('month', created_at) ORDER BY month");
        List<Map<String, Object>> businessesByMonth = ((List<Object[]>) bizQuery.getResultList()).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("month", r[0]);
            m.put("count", r[1]);
            return m;
        }).toList();

        result.put("usersByMonth", usersByMonth);
        result.put("invoicesByMonth", invoicesByMonth);
        result.put("revenueByMonth", revenueByMonth);
        result.put("customersByMonth", customersByMonth);
        result.put("businessesByMonth", businessesByMonth);

        // Invoices by status per month
        Query invStatusQuery = entityManager.createNativeQuery(
            "SELECT TO_CHAR(date_trunc('month', created_at), 'YYYY-MM') AS month, status, COUNT(*) AS count " +
            "FROM invoices GROUP BY date_trunc('month', created_at), status ORDER BY month, status");
        List<Object[]> invStatusRows = invStatusQuery.getResultList();
        // Group by month
        Map<String, Map<String, Long>> statusByMonth = new LinkedHashMap<>();
        for (Object[] row : invStatusRows) {
            String month = (String) row[0];
            String status = (String) row[1];
            Long count = ((Number) row[2]).longValue();
            statusByMonth.computeIfAbsent(month, k -> new LinkedHashMap<>()).put(status, count);
        }
        List<Map<String, Object>> invoicesByStatus = new java.util.ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : statusByMonth.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("month", entry.getKey());
            m.putAll(entry.getValue());
            invoicesByStatus.add(m);
        }
        result.put("invoicesByStatus", invoicesByStatus);

        return ResponseEntity.ok(ApiResponse.success("Analytics retrieved", result));
    }

    @GetMapping("/businesses")
    @Operation(summary = "Get all registered businesses (Admin only)")
    public ResponseEntity<ApiResponse<List<BusinessResponse>>> getAllBusinesses() {
        List<Business> businesses = businessRepository.findAll();
        List<BusinessResponse> result = businesses.stream()
                .map(businessMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Businesses retrieved", result));
    }

    @GetMapping("/businesses/{id}")
    @Operation(summary = "Get business by ID (Admin only)")
    public ResponseEntity<ApiResponse<BusinessResponse>> getBusiness(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found with id: " + id));
        return ResponseEntity.ok(ApiResponse.success("Business retrieved", businessMapper.toResponse(business)));
    }

    @GetMapping("/businesses/{businessId}/invoices")
    @Operation(summary = "Get all invoices for a business (Admin only)")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> getBusinessInvoices(
            @PathVariable Long businessId) {
        List<Invoice> invoices = invoiceRepository.findAllByBusinessId(businessId);
        List<InvoiceResponse> result = invoices.stream()
                .map(invoice -> {
                    String customerName = customerRepository.findById(invoice.getCustomerId())
                            .map(Customer::getName)
                            .orElse("Unknown");
                    return invoiceMapper.toResponse(invoice, customerName);
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Business invoices retrieved", result));
    }

    @GetMapping("/invoices")
    @Operation(summary = "Get all invoices across all businesses (Admin only)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllInvoices() {
        List<Invoice> invoices = invoiceRepository.findAll(
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> result = invoices.stream().map(inv -> {
            String customerName = customerRepository.findById(inv.getCustomerId())
                    .map(com.insideinvoice.customer.entity.Customer::getName)
                    .orElse("Unknown");
            String businessName = businessRepository.findById(inv.getBusinessId())
                    .map(com.insideinvoice.business.entity.Business::getBusinessName)
                    .orElse("Unknown");
            String ownerName = userRepository.findById(inv.getCreatedBy())
                    .map(com.insideinvoice.auth.entity.User::getName)
                    .orElse("Unknown");
            Map<String, Object> m = new HashMap<>();
            m.put("id", inv.getId());
            m.put("invoiceNumber", inv.getInvoiceNumber());
            m.put("invoiceType", inv.getInvoiceType().name());
            m.put("customerId", inv.getCustomerId());
            m.put("customerName", customerName);
            m.put("businessName", businessName);
            m.put("ownerName", ownerName);
            m.put("invoiceDate", inv.getInvoiceDate().toString());
            m.put("dueDate", inv.getDueDate().toString());
            m.put("grandTotal", inv.getGrandTotal());
            m.put("status", inv.getStatus().name());
            m.put("placeOfSupply", inv.getPlaceOfSupply());
            m.put("destination", inv.getDestination());
            return m;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved", result));
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Get invoice by ID (Admin only)")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(@PathVariable Long id) {
        InvoiceResponse response = invoiceService.getInvoiceById(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved", response));
    }

    @PutMapping("/invoices/{id}")
    @Operation(summary = "Update invoice by ID (Admin only)")
    public ResponseEntity<ApiResponse<InvoiceResponse>> updateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInvoiceRequest request) {
        InvoiceResponse response = invoiceService.updateInvoiceById(id, request);
        return ResponseEntity.ok(ApiResponse.success("Invoice updated", response));
    }

    @GetMapping("/customers")
    @Operation(summary = "Get all customers across all businesses (Admin only)")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAllCustomers() {
        List<CustomerResponse> result = customerRepository.findAll().stream()
                .map(customerMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Customers retrieved", result));
    }

    @PutMapping("/users/{id}/password")
    @Operation(summary = "Update a user's password (Admin only)")
    public ResponseEntity<ApiResponse<String>> updatePassword(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRawPassword(request.getPassword());
        userRepository.save(user);

        log.info("Admin updated password for user: {}", user.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully"));
    }

    @PutMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role (Admin only)")
    public ResponseEntity<ApiResponse<String>> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getEmail().equals(currentEmail)) {
            throw new BadRequestException("Cannot change your own role");
        }

        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new BadRequestException("Cannot revoke the last admin");
            }
        }

        String newRole = body.get("role");
        if (newRole == null || (!newRole.equals("ADMIN") && !newRole.equals("USER"))) {
            throw new BadRequestException("Role must be ADMIN or USER");
        }

        user.setRole(Role.valueOf(newRole));
        userRepository.save(user);

        log.info("Admin changed role for user {} to {}", user.getEmail(), newRole);
        return ResponseEntity.ok(ApiResponse.success("Role updated to " + newRole));
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete a user and their business data (Admin only)")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getEmail().equals(currentEmail)) {
            throw new BadRequestException("Cannot delete yourself");
        }

        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new BadRequestException("Cannot delete the last admin");
            }
        }

        Long businessId = user.getBusinessId();

        productRepository.deleteByBusinessId(businessId);
        customerRepository.deleteByBusinessId(businessId);
        invoiceRepository.deleteByBusinessId(businessId);

        userRepository.delete(user);

        long remainingUsers = userRepository.countByBusinessId(businessId);
        if (remainingUsers == 0) {
            businessRepository.deleteById(businessId);
            log.info("Deleted business {} as well (no remaining users)", businessId);
        }

        log.info("Admin deleted user: {}", user.getEmail());
        return ResponseEntity.ok(ApiResponse.success("User and their data deleted successfully"));
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Update user details (Admin only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (updates.containsKey("name")) {
            user.setName((String) updates.get("name"));
        }
        if (updates.containsKey("email")) {
            String newEmail = (String) updates.get("email");
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                throw new BadRequestException("Email already in use");
            }
            user.setEmail(newEmail);
        }
        if (updates.containsKey("username")) {
            String newUsername = (String) updates.get("username");
            if (!newUsername.equals(user.getUsername()) && userRepository.existsByUsername(newUsername)) {
                throw new BadRequestException("Username already in use");
            }
            user.setUsername(newUsername);
        }

        userRepository.save(user);
        log.info("Admin updated user: {}", user.getEmail());

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("name", user.getName());
        result.put("email", user.getEmail());
        result.put("username", user.getUsername());
        result.put("role", user.getRole().name());
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", result));
    }

    @GetMapping("/products")
    @Operation(summary = "Get all registered products across all businesses (Admin only)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllProducts() {
        List<Product> products = productRepository.findAll();
        List<Map<String, Object>> result = products.stream().map(p -> {
            Business business = businessRepository.findById(p.getBusinessId()).orElse(null);
            String businessName = business != null ? business.getBusinessName() : "Unknown";
            String ownerName = business != null ? business.getOwnerName() : "Unknown";
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("hsn", p.getHsn());
            m.put("rate", p.getRate());
            m.put("gstPercentage", p.getGstPercentage());
            m.put("businessName", businessName);
            m.put("ownerName", ownerName);
            m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success("Products retrieved", result));
    }
}
