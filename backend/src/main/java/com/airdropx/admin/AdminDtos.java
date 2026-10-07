package com.airdropx.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record RecentAuditLog(UUID id, String action, String entityType, UUID entityId, String actorEmail, Instant createdAt) {}

record AdminDashboardSummary(
        long totalCompanies,
        long totalUsers,
        Map<String, Long> airdropsByStatus,
        long totalRecipientsCompleted,
        long totalRecipientsFailed,
        List<RecentAuditLog> recentActivity
) {}

record CompanySummary(
        UUID id, String name, String email, String status,
        long userCount, long airdropCount, Instant createdAt
) {}

record AdminUserSummary(
        UUID id, String email, String firstName, String lastName, String role, String status,
        UUID companyId, String companyName, Instant createdAt
) {}

record AdminAirdropSummary(
        UUID id, String name, String status, UUID companyId, String companyName,
        BigDecimal totalAmount, int recipientCount, Instant createdAt
) {}

record AuditLogSummary(
        UUID id, String action, String entityType, UUID entityId, String actorEmail,
        UUID companyId, String companyName, String ipAddress, Instant createdAt
) {}
