package com.insideinvoice.admin.controller;

import com.insideinvoice.admin.dto.AdminStatsResponse;
import com.insideinvoice.admin.dto.UpdatePasswordRequest;
import com.insideinvoice.admin.dto.UserWithPasswordResponse;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.repository.InvoiceRepository;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
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
}
