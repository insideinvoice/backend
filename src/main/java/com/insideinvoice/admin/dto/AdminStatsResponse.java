package com.insideinvoice.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatsResponse {

    private long totalUsers;
    private long totalBusinesses;
    private long totalInvoices;
    private long totalCustomers;
    private long totalProducts;
    private List<UserSummary> recentUsers;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserSummary {
        private Long id;
        private String name;
        private String email;
        private String role;
        private Long businessId;
        private String createdAt;
    }
}
