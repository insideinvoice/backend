package com.insideinvoice.admin.controller;

import com.insideinvoice.admin.dto.AdminStatsResponse;
import com.insideinvoice.admin.dto.SalesAnalyticsResponse;
import com.insideinvoice.admin.dto.UpdatePasswordRequest;
import com.insideinvoice.admin.dto.UserWithPasswordResponse;
import com.insideinvoice.admin.service.AdminDeletionService;
import com.insideinvoice.admin.service.AdminSalesAnalyticsService;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.dto.response.BusinessResponse;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.mapper.BusinessMapper;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.common.PageParams;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.dto.request.CreateCustomerRequest;
import com.insideinvoice.customer.dto.response.CustomerResponse;
import com.insideinvoice.customer.mapper.CustomerMapper;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.customer.service.CustomerService;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.exception.UnauthorizedException;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.UpdateInvoiceRequest;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.mapper.InvoiceMapper;
import com.insideinvoice.invoice.repository.InvoiceItemRepository;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.service.InvoiceService;
import com.insideinvoice.product.dto.response.ProductResponse;
import com.insideinvoice.product.entity.Product;
import com.insideinvoice.product.mapper.ProductMapper;
import com.insideinvoice.product.repository.ProductRepository;
import com.insideinvoice.product.service.ProductService;
import com.insideinvoice.deliverychallan.dto.response.DeliveryChallanResponse;
import com.insideinvoice.deliverychallan.entity.DeliveryChallan;
import com.insideinvoice.deliverychallan.repository.DeliveryChallanRepository;
import com.insideinvoice.deliverychallan.service.DeliveryChallanService;
import com.insideinvoice.labels.dto.response.HazmatLabelResponse;
import com.insideinvoice.labels.entity.HazmatLabel;
import com.insideinvoice.labels.entity.ShippingLabel;
import com.insideinvoice.labels.repository.HazmatLabelRepository;
import com.insideinvoice.labels.repository.LabelFileRepository;
import com.insideinvoice.labels.repository.ShippingLabelRepository;
import com.insideinvoice.labels.service.HazmatLabelService;
import com.insideinvoice.labels.service.ShippingLabelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final CustomerService customerService;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final AdminDeletionService adminDeletionService;
    private final DeliveryChallanService deliveryChallanService;
    private final DeliveryChallanRepository deliveryChallanRepository;
    private final HazmatLabelService hazmatLabelService;
    private final HazmatLabelRepository hazmatLabelRepository;
    private final ShippingLabelService shippingLabelService;
    private final ShippingLabelRepository shippingLabelRepository;
    private final LabelFileRepository labelFileRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final AdminSalesAnalyticsService salesAnalyticsService;

    @GetMapping("/users")
    @Operation(summary = "Get users page with passwords (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<UserWithPasswordResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        // Page-scoped query: only this page's rows are hydrated and only their
        // users contribute to the last-activity GROUP BY (bounded by idx_invoices_created_by_created_at).
        Page<User> users = userRepository.search(normalize(q), activeOnly,
                LocalDateTime.now().minusDays(30),
                PageRequest.of(Math.max(page, 0), PageParams.clampSize(size),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<Long, LocalDateTime> lastActivity = lastActivityFor(users.getContent());
        List<UserWithPasswordResponse> rows = users.getContent().stream()
                .map(u -> toUserResponse(u, lastActivity))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", toPaged(users, rows)));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get one user with password (Admin workspace direct lookup)")
    public ResponseEntity<ApiResponse<UserWithPasswordResponse>> getUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return ResponseEntity.ok(ApiResponse.success("User retrieved",
                toUserResponse(user, lastActivityFor(List.of(user)))));
    }

    /**
     * Last-activity restricted to this page of users. The old platform-wide
     * GROUP BY ran over the entire invoices table on every list request just to
     * decorate a handful of rows.
     */
    private Map<Long, LocalDateTime> lastActivityFor(List<User> pageUsers) {
        Set<Long> ids = pageUsers.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LocalDateTime> lastActivity = new HashMap<>();
        if (ids.isEmpty()) {
            return lastActivity;
        }
        for (Object[] row : invoiceRepository.lastActivityForUsers(ids)) {
            lastActivity.put((Long) row[0], (LocalDateTime) row[1]);
        }
        return lastActivity;
    }

    private static UserWithPasswordResponse toUserResponse(User u, Map<Long, LocalDateTime> lastActivity) {
        LocalDateTime acts = lastActivity.get(u.getId());
        LocalDateTime activeCutoff = LocalDateTime.now().minusDays(30);
        return UserWithPasswordResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole().name())
                .password(u.getPassword())
                .rawPassword(u.getRawPassword())
                .businessId(u.getBusinessId())
                .createdAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
                .lastActivityAt(acts != null ? acts.toString() : null)
                .active(acts != null && acts.isAfter(activeCutoff))
                .build();
    }

    /** Shared PagedResponse assembly — mirrors every tenant service. The Page carries only paging metadata; rows may be mapped DTOs. */
    private static <T> PagedResponse<T> toPaged(Page<?> page, List<T> rows) {
        return PagedResponse.<T>builder()
                .content(rows)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }

    private static String normalize(String q) {
        return q == null ? "" : q.trim();
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

        // Last-30-days window activity for the dashboard's Active Users KPI +
        // the Active Users / Invoices Generated charts below Monthly Sales.
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);
        long activeUsersLast30Days = 0;
        long invoicesLast30Days = 0;
        List<Object[]> periodRows = invoiceRepository.periodTotalsSince(cutoff);
        if (!periodRows.isEmpty() && periodRows.get(0) != null && periodRows.get(0).length >= 2) {
            activeUsersLast30Days = ((Number) periodRows.get(0)[0]).longValue();
            invoicesLast30Days = ((Number) periodRows.get(0)[1]).longValue();
        }

        Map<String, AdminStatsResponse.DailyActivity> activityByDayAll = new HashMap<>();
        for (Object[] row : invoiceRepository.dailyActivitySince(cutoff)) {
            if (row.length < 3 || row[0] == null) continue;
            String day = ((java.sql.Date) row[0]).toLocalDate().toString();
            activityByDayAll.put(day, AdminStatsResponse.DailyActivity.builder()
                    .date(day)
                    .invoices(((Number) row[1]).longValue())
                    .activeUsers(((Number) row[2]).longValue())
                    .build());
        }
        List<AdminStatsResponse.DailyActivity> dailyActivity = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            String day = today.minusDays(i).toString();
            AdminStatsResponse.DailyActivity a = activityByDayAll.get(day);
            dailyActivity.add(a != null ? a : AdminStatsResponse.DailyActivity.builder()
                    .date(day).invoices(0).activeUsers(0).build());
        }

        // New users per month (last 6) + the calling admin's OWN business invoices
        // per month (last 12) — "I sold it, I generate the invoice" operator charts.
        LocalDateTime signupCutoff = LocalDate.now().minusMonths(5).withDayOfMonth(1).atStartOfDay();
        List<AdminStatsResponse.MonthlyCount> newUsersByMonth = fillMonths(
                userRepository.monthlySignupCount(signupCutoff.atOffset(ZoneOffset.UTC)), 6);
        Long ownBusinessId = currentAdminBusinessId();
        LocalDateTime ownCutoff = LocalDate.now().minusMonths(11).withDayOfMonth(1).atStartOfDay();
        List<AdminStatsResponse.MonthlyCount> ownInvoicesByMonth = ownBusinessId == null ? List.of()
                : fillMonths(invoiceRepository.monthlyCountByBusiness(ownBusinessId,
                        ownCutoff.atOffset(ZoneOffset.UTC)), 12);

        AdminStatsResponse stats = AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalBusinesses(totalBusinesses)
                .totalInvoices(totalInvoices)
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .activeUsersLast30Days(activeUsersLast30Days)
                .invoicesLast30Days(invoicesLast30Days)
                .dailyActivity(dailyActivity)
                .newUsersByMonth(newUsersByMonth)
                .ownInvoicesByMonth(ownInvoicesByMonth)
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
    @Operation(summary = "Get registered businesses page (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<BusinessResponse>>> getAllBusinesses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Pageable pageable = PageParams.of(page, size, PageParams.safeSort(sortBy, sortDir,
                Set.of("createdAt", "businessName", "ownerName", "email", "phone", "gstIn"), "createdAt"));
        Page<Business> businesses = businessRepository.searchAll(normalize(q), pageable);
        List<BusinessResponse> rows = businesses.getContent().stream()
                .map(businessMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Businesses retrieved", toPaged(businesses, rows)));
    }

    @GetMapping("/sales-analytics")
    @Operation(summary = "SQL-side sales aggregates (platform-wide, or one business via ?businessId=)")
    public ResponseEntity<ApiResponse<SalesAnalyticsResponse>> getSalesAnalytics(
            @RequestParam(required = false) Long businessId) {
        if (businessId != null) {
            requireBusiness(businessId);
        }
        return ResponseEntity.ok(ApiResponse.success("Sales analytics retrieved",
                salesAnalyticsService.getSalesAnalytics(businessId)));
    }

    @GetMapping("/businesses/{id}")
    @Operation(summary = "Get business by ID (Admin only)")
    public ResponseEntity<ApiResponse<BusinessResponse>> getBusiness(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found with id: " + id));
        return ResponseEntity.ok(ApiResponse.success("Business retrieved", businessMapper.toResponse(business)));
    }

    @GetMapping("/businesses/{businessId}/invoices")
    @Operation(summary = "Get invoices page for a business (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceResponse>>> getBusinessInvoices(
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Pageable pageable = PageParams.of(page, size, PageParams.safeSort(sortBy, sortDir,
                Set.of("createdAt", "invoiceDate", "dueDate", "grandTotal", "invoiceNumber", "status"), "createdAt"));
        Page<Invoice> invoices = invoiceRepository.searchByBusinessId(businessId, normalize(q), pageable);
        List<Invoice> content = invoices.getContent();

        Set<Long> customerIds = content.stream()
                .map(Invoice::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> customerNames = customerIds.isEmpty() ? Map.of() :
                customerRepository.findAllById(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));

        // ONE items query for the whole page — the old JOIN FETCH dragged every
        // invoice + every line item of the business into memory for a list view;
        // per-page batch loading keeps the mapper lazy-safe without the N+1.
        List<Long> invoiceIds = content.stream().map(Invoice::getId).toList();
        Map<Long, List<InvoiceItem>> itemsByInvoice = invoiceIds.isEmpty() ? Map.of() :
                invoiceItemRepository.findByInvoiceIdIn(invoiceIds).stream()
                        .collect(Collectors.groupingBy(it -> it.getInvoice().getId()));

        List<InvoiceResponse> rows = content.stream()
                .map(invoice -> invoiceMapper.toResponse(invoice,
                        customerNames.getOrDefault(invoice.getCustomerId(), "Unknown"),
                        itemsByInvoice.getOrDefault(invoice.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Business invoices retrieved", toPaged(invoices, rows)));
    }

    // ------------------------------------------------------------------
    // Per-user workspace (admin triage console). Every operation delegates
    // to the SAME services merchants use, with the resolved businessId
    // passed explicitly — so all business rules apply unchanged: invoice
    // numbering + sequence reservation, customer duplicate checks, and the
    // FK delete guards ("payments recorded", "labels reference it",
    // "invoices exist for this customer"). Nothing here raw-deletes.
    // ------------------------------------------------------------------

    @GetMapping("/businesses/{businessId}/customers")
    @Operation(summary = "Get customers page of a specific business (Admin workspace)")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> getBusinessCustomers(
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        requireBusiness(businessId);
        Pageable pageable = PageParams.of(page, size, PageParams.safeSort(sortBy, sortDir,
                Set.of("createdAt", "name", "email", "phone", "city", "state"), "createdAt"));
        Page<Customer> customers = customerRepository.searchByBusinessId(businessId, normalize(q), pageable);
        List<CustomerResponse> rows = customers.getContent().stream()
                .map(customerMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Business customers retrieved", toPaged(customers, rows)));
    }

    @PostMapping("/businesses/{businessId}/customers")
    @Operation(summary = "Create a customer inside a specific business (Admin workspace)")
    public ResponseEntity<ApiResponse<CustomerResponse>> createBusinessCustomer(
            @PathVariable Long businessId,
            @Valid @RequestBody CreateCustomerRequest request) {
        requireBusiness(businessId);
        CustomerResponse response = customerService.createCustomer(request, businessId);
        log.info("Admin created customer for businessId: {}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Customer created", response));
    }

    @GetMapping("/businesses/{businessId}/products")
    @Operation(summary = "Get products page of a specific business (Admin workspace)")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getBusinessProducts(
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Business business = requireBusiness(businessId);
        Pageable pageable = PageParams.of(page, size, PageParams.safeSort(sortBy, sortDir,
                Set.of("createdAt", "name", "hsn", "rate", "gstPercentage"), "createdAt"));
        Page<Product> products = productRepository.searchByBusinessId(businessId, normalize(q), pageable);
        List<Map<String, Object>> rows = products.getContent().stream()
                .map(p -> productSummary(p, business))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Business products retrieved", toPaged(products, rows)));
    }

    @PostMapping("/businesses/{businessId}/invoices")
    @Operation(summary = "Create an invoice inside a user's business (Admin on behalf of)")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoiceForBusiness(
            @PathVariable Long businessId,
            @Valid @RequestBody CreateInvoiceRequest request) {
        Business business = requireBusiness(businessId);
        // Record the invoice under the account that owns the business (createdBy
        // drives owner display elsewhere); "on behalf of" must look identical to
        // a self-created invoice.
        Long ownerUserId = userRepository.findByBusinessIdOrderByIdAsc(business.getId()).stream()
                .findFirst()
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "businessId", businessId));
        InvoiceResponse response = invoiceService.createInvoice(request, business.getId(), ownerUserId);
        log.info("Admin created invoice {} on behalf of businessId: {}", response.getInvoiceNumber(), businessId);
        return ResponseEntity.ok(ApiResponse.success("Invoice created", response));
    }

    @DeleteMapping("/invoices/{invoiceId}")
    @Operation(summary = "Delete any user's invoice (Admin override)")
    public ResponseEntity<ApiResponse<String>> deleteInvoiceAsAdmin(@PathVariable Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        // Same guards as the tenant delete (payments / labels FK checks) because
        // the implementation is shared; the admin just skips the tenant match.
        invoiceService.deleteInvoice(invoice.getId(), invoice.getBusinessId());
        log.info("Admin deleted invoice: {} (businessId: {})", invoiceId, invoice.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Invoice deleted successfully"));
    }

    @DeleteMapping("/customers/{customerId}")
    @Operation(summary = "Delete any user's customer (Admin override)")
    public ResponseEntity<ApiResponse<String>> deleteCustomerAsAdmin(@PathVariable Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        // Same guard as the tenant delete: refuses with an actionable 400 while
        // invoices still reference this customer.
        customerService.deleteCustomer(customer.getId(), customer.getBusinessId());
        log.info("Admin deleted customer: {} (businessId: {})", customerId, customer.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully"));
    }

    @DeleteMapping("/products/{productId}")
    @Operation(summary = "Delete any user's product (Admin override)")
    public ResponseEntity<ApiResponse<String>> deleteProductAsAdmin(@PathVariable Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        // Same service method the owner's delete uses; passing the product's own
        // businessId satisfies the tenant lookup inside so the admin can delete
        // products belonging to any business.
        productService.deleteProduct(product.getId(), product.getBusinessId());
        log.info("Admin deleted product: {} (businessId: {})", productId, product.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }

    // ------------------------------------------------------------ challans & labels (admin triage)

    @GetMapping("/challans")
    @Operation(summary = "Get delivery challans page across all businesses (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getAllChallans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q) {
        // ORDER BY created_at DESC lives in the native query (rides the global
        // index), so the Pageable carries no Sort; name lookups batch only this page.
        Pageable pageable = PageRequest.of(Math.max(page, 0), PageParams.clampSize(size));
        Page<DeliveryChallan> challans = deliveryChallanRepository.searchAll(normalize(q), pageable);
        List<DeliveryChallan> content = challans.getContent();

        Set<Long> customerIds = content.stream().map(DeliveryChallan::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> businessIds = content.stream().map(DeliveryChallan::getBusinessId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> customerNames = customerIds.isEmpty() ? Map.of() :
                customerRepository.findAllById(customerIds).stream().collect(Collectors.toMap(com.insideinvoice.customer.entity.Customer::getId, com.insideinvoice.customer.entity.Customer::getName, (a, b) -> a));
        Map<Long, String> businessNames = businessIds.isEmpty() ? Map.of() :
                businessRepository.findAllById(businessIds).stream().collect(Collectors.toMap(Business::getId, Business::getBusinessName, (a, b) -> a));

        List<Map<String, Object>> rows = content.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("challanNumber", c.getChallanNumber());
            row.put("challanDate", c.getChallanDate());
            row.put("customerId", c.getCustomerId());
            row.put("customerName", customerNames.get(c.getCustomerId()));
            row.put("businessId", c.getBusinessId());
            row.put("businessName", businessNames.get(c.getBusinessId()));
            row.put("createdAt", c.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Delivery challans retrieved", toPaged(challans, rows)));
    }

    @GetMapping("/challans/{id}")
    @Operation(summary = "Get any user's delivery challan (Admin override)")
    public ResponseEntity<ApiResponse<DeliveryChallanResponse>> getChallanAsAdmin(@PathVariable Long id) {
        DeliveryChallan challan = deliveryChallanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery challan", "id", id));
        return ResponseEntity.ok(ApiResponse.success("Delivery challan retrieved",
                deliveryChallanService.getDeliveryChallan(challan.getId(), challan.getBusinessId())));
    }

    @DeleteMapping("/challans/{id}")
    @Operation(summary = "Delete any user's delivery challan (Admin override)")
    public ResponseEntity<ApiResponse<String>> deleteChallanAsAdmin(@PathVariable Long id) {
        DeliveryChallan challan = deliveryChallanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery challan", "id", id));
        deliveryChallanService.deleteDeliveryChallan(challan.getId(), challan.getBusinessId());
        log.info("Admin deleted delivery challan: {} (businessId: {})", id, challan.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Delivery challan deleted successfully"));
    }

    @GetMapping("/labels/hazmat/{id}")
    @Operation(summary = "Get any user's hazmat label (Admin override)")
    public ResponseEntity<ApiResponse<HazmatLabelResponse>> getHazmatLabelAsAdmin(@PathVariable Long id) {
        HazmatLabel label = hazmatLabelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HazmatLabel", "id", id));
        return ResponseEntity.ok(ApiResponse.success("Hazmat label retrieved",
                hazmatLabelService.get(label.getId(), label.getBusinessId())));
    }

    /**
     * Hard purge (mirrors AdminDeletionService's FK-safe order). The label service's
     * own delete is a soft delete that KEEPS the fk_labels_invoice row, so it can
     * never free the invoice; the purge is what removes the physical blocker.
     */
    @DeleteMapping("/labels/hazmat/{id}")
    @Operation(summary = "Hard-delete any user's hazmat label + its files/audit rows (Admin override)")
    public ResponseEntity<ApiResponse<String>> deleteHazmatLabelAsAdmin(@PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        HazmatLabel label = hazmatLabelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HazmatLabel", "id", id));
        labelFileRepository.deleteByLabelIdIn(Set.of(label.getId()));
        hazmatLabelRepository.delete(label);
        log.info("Admin purged hazmat label: {} (businessId: {}, ip: {})", id, label.getBusinessId(), clientIp(request));
        return ResponseEntity.ok(ApiResponse.success("Hazmat label deleted successfully"));
    }

    @GetMapping("/labels/shipping/{id}")
    @Operation(summary = "Get any user's shipping label (Admin override)")
    public ResponseEntity<ApiResponse<Object>> getShippingLabelAsAdmin(@PathVariable Long id) {
        ShippingLabel label = shippingLabelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingLabel", "id", id));
        return ResponseEntity.ok(ApiResponse.success("Shipping label retrieved",
                shippingLabelService.get(label.getId(), label.getBusinessId())));
    }

    @DeleteMapping("/labels/shipping/{id}")
    @Operation(summary = "Hard-delete any user's shipping label + its files rows (Admin override)")
    public ResponseEntity<ApiResponse<String>> deleteShippingLabelAsAdmin(@PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        ShippingLabel label = shippingLabelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingLabel", "id", id));
        labelFileRepository.deleteByLabelIdIn(Set.of(label.getId()));
        shippingLabelRepository.delete(label);
        log.info("Admin purged shipping label: {} (businessId: {}, ip: {})", id, label.getBusinessId(), clientIp(request));
        return ResponseEntity.ok(ApiResponse.success("Shipping label deleted successfully"));
    }

    @GetMapping("/labels")
    @Operation(summary = "Get EVERY label row attached to an invoice, incl. soft-deleted (Admin triage: invoice-delete blockers)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLabelsForInvoice(@RequestParam Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        List<Map<String, Object>> hazmat = hazmatLabelRepository.findByInvoiceId(invoice.getId()).stream()
                .map(l -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", l.getId());
                    row.put("kind", "hazmat");
                    row.put("labelNumber", l.getLabelNumber());
                    row.put("title", l.getProperShippingName());
                    row.put("softDeleted", l.getDeletedAt() != null);
                    return row;
                }).collect(Collectors.toList());
        List<Map<String, Object>> shipping = shippingLabelRepository.findByInvoiceId(invoice.getId()).stream()
                .map(l -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", l.getId());
                    row.put("kind", "shipping");
                    row.put("labelNumber", l.getLabelNumber());
                    row.put("title", l.getCarrier() != null ? l.getCarrier() : "shipping label");
                    row.put("softDeleted", l.getDeletedAt() != null);
                    return row;
                }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoiceId", invoice.getId());
        result.put("blocksDelete", !shipping.isEmpty() || !hazmat.isEmpty());
        result.put("hazmat", hazmat);
        result.put("shipping", shipping);
        return ResponseEntity.ok(ApiResponse.success("Invoice labels retrieved", result));
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        return request != null ? request.getRemoteAddr() : null;
    }

    /** Turns DATE_TRUNC rows into contiguous "YYYY-MM" buckets ending this month, zero-filled. */
    private List<AdminStatsResponse.MonthlyCount> fillMonths(List<Object[]> rows, int months) {
        Map<String, Long> byMonth = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length < 2 || row[0] == null) continue;
            // JDBC driver/hibernate mapping has varied: Instant (native DATE_TRUNC),
            // Timestamp, or Date all observed across environments — tolerate each.
            LocalDate monthStart;
            if (row[0] instanceof java.time.Instant instant) {
                monthStart = instant.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
            } else if (row[0] instanceof java.sql.Timestamp ts) {
                monthStart = ts.toLocalDateTime().toLocalDate().withDayOfMonth(1);
            } else if (row[0] instanceof java.sql.Date d) {
                monthStart = d.toLocalDate().withDayOfMonth(1);
            } else {
                continue;
            }
            String key = monthStart.toString().substring(0, 7);
            byMonth.merge(key, ((Number) row[1]).longValue(), Long::sum);
        }
        List<AdminStatsResponse.MonthlyCount> result = new ArrayList<>();
        LocalDate anchor = LocalDate.now();
        for (int i = months - 1; i >= 0; i--) {
            String key = anchor.minusMonths(i).withDayOfMonth(1).toString().substring(0, 7);
            result.add(AdminStatsResponse.MonthlyCount.builder()
                    .month(key)
                    .count(byMonth.getOrDefault(key, 0L))
                    .build());
        }
        return result;
    }

    private Long currentAdminBusinessId() {
        User admin = userRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElse(null);
        return admin != null ? admin.getBusinessId() : null;
    }

    private Business requireBusiness(Long businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found with id: " + businessId));
    }

    @GetMapping("/invoices")
    @Operation(summary = "Get invoices page across all businesses (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getAllInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q) {
        // One joined native query resolves search + display names for the page
        // (the old path loaded EVERY invoice, then ran three findAllById batches).
        Pageable pageable = PageRequest.of(Math.max(page, 0), PageParams.clampSize(size));
        Page<Invoice> invoices = invoiceRepository.searchAll(normalize(q), pageable);
        List<Invoice> content = invoices.getContent();

        Set<Long> customerIds = content.stream().map(Invoice::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> businessIds = content.stream().map(Invoice::getBusinessId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = content.stream().map(Invoice::getCreatedBy).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> customerNames = customerIds.isEmpty() ? Map.of() :
                customerRepository.findAllById(customerIds).stream().collect(Collectors.toMap(com.insideinvoice.customer.entity.Customer::getId, com.insideinvoice.customer.entity.Customer::getName, (a, b) -> a));
        Map<Long, String> businessNames = businessIds.isEmpty() ? Map.of() :
                businessRepository.findAllById(businessIds).stream().collect(Collectors.toMap(com.insideinvoice.business.entity.Business::getId, com.insideinvoice.business.entity.Business::getBusinessName, (a, b) -> a));
        Map<Long, String> userNames = userIds.isEmpty() ? Map.of() :
                userRepository.findAllById(userIds).stream().collect(Collectors.toMap(com.insideinvoice.auth.entity.User::getId, com.insideinvoice.auth.entity.User::getName, (a, b) -> a));

        List<Map<String, Object>> rows = content.stream().map(inv -> {
            String customerName = customerNames.getOrDefault(inv.getCustomerId(), "Unknown");
            String businessName = businessNames.getOrDefault(inv.getBusinessId(), "Unknown");
            String ownerName = userNames.getOrDefault(inv.getCreatedBy(), "Unknown");
            Map<String, Object> m = new LinkedHashMap<>();
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
            m.put("taxMode", inv.getTaxMode() == null ? null : inv.getTaxMode().name());
            m.put("destination", inv.getDestination());
            return m;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved", toPaged(invoices, rows)));
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
    @Operation(summary = "Get customers page across all businesses (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Pageable pageable = PageParams.of(page, size, PageParams.safeSort(sortBy, sortDir,
                Set.of("createdAt", "name", "email", "phone", "city", "state", "gstIn"), "createdAt"));
        Page<Customer> customers = customerRepository.searchAll(normalize(q), pageable);
        List<CustomerResponse> rows = customers.getContent().stream()
                .map(customerMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Customers retrieved", toPaged(customers, rows)));
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

        // Cross-confirmation: the acting admin must re-enter their own password so a
        // hijacked/unsupervised session cannot silently grant or revoke admin rights.
        String adminPassword = body.get("adminPassword");
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new BadRequestException("Enter your (admin) password to confirm this change");
        }
        User actingAdmin = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new UnauthorizedException("Admin account not found. Please sign in again."));
        if (!passwordEncoder.matches(adminPassword, actingAdmin.getPassword())) {
            throw new UnauthorizedException("Incorrect password. Role change cancelled.");
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

        adminDeletionService.deleteUserWithBusinessData(user);

        log.info("Admin deleted user: {}", user.getEmail());
        return ResponseEntity.ok(ApiResponse.success("User and their data deleted successfully"));
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Update user details (Admin only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (updates.containsKey("name")) {
            user.setName(stringField(updates, "name"));
        }
        if (updates.containsKey("email")) {
            String newEmail = stringField(updates, "email");
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                throw new BadRequestException("Email already in use");
            }
            user.setEmail(newEmail);
        }
        if (updates.containsKey("username")) {
            String newUsername = stringField(updates, "username");
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

    /**
     * Typed extraction for the ad-hoc update map. The old code cast blindly
     * ({@code (String) updates.get("name")} → ClassCastException on a JSON number) and
     * dereferenced nulls ({@code newEmail.equals(...)} → NPE when the client sent
     * {@code {"email": null}}), both surfacing as 500s.
     */
    private static String stringField(Map<String, Object> updates, String key) {
        Object value = updates.get(key);
        if (value == null) {
            throw new BadRequestException("Field '" + key + "' must not be null");
        }
        if (!(value instanceof String text)) {
            throw new BadRequestException("Field '" + key + "' must be text");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Field '" + key + "' must not be blank");
        }
        return trimmed;
    }

    @GetMapping("/products")
    @Operation(summary = "Get products page across all businesses (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<Map<String, Object>>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Pageable pageable = PageParams.of(page, size, PageParams.safeSort(sortBy, sortDir,
                Set.of("createdAt", "name", "hsn", "rate", "gstPercentage"), "createdAt"));
        Page<Product> pageProducts = productRepository.searchAll(normalize(q), pageable);

        // Batch-load the owning businesses for THIS page only. productSummary(p, null)
        // used to fall back to findById per row → an N+1 across the whole platform list.
        Set<Long> businessIds = pageProducts.getContent().stream()
                .map(Product::getBusinessId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Business> businessesById = businessIds.isEmpty() ? Map.of() :
                businessRepository.findAllById(businessIds).stream()
                        .collect(Collectors.toMap(Business::getId, b -> b, (a, b) -> a));
        List<Map<String, Object>> rows = pageProducts.getContent().stream()
                .map(p -> productSummary(p, businessesById.get(p.getBusinessId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Products retrieved", toPaged(pageProducts, rows)));
    }

    /**
     * Same view shape for the platform product list and the per-business
     * workspace list. When the business is already resolved (workspace) it is
     * reused instead of issuing one findById per product.
     */
    private Map<String, Object> productSummary(Product p, Business resolvedBusiness) {
        Business business = resolvedBusiness != null
                ? resolvedBusiness
                : businessRepository.findById(p.getBusinessId()).orElse(null);
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
    }
}
