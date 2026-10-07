package com.airdropx.admin;

import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.common.enums.RecipientStatus;
import com.airdropx.model.*;
import com.airdropx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every method reads across all companies — there is no companyId scoping here, unlike the user module.
 * Access is restricted at the filter-chain level (SecurityConfig requires ROLE_PLATFORM_ADMIN for all
 * of /api/v1/admin/**), not per-query, since this data is meant to be platform-wide by design.
 *
 * Methods are @Transactional(readOnly = true) because several DTO mappings below traverse a lazy
 * @ManyToOne (user.getCompany().getName(), airdrop.getCompany().getName()) — without an open session
 * during that traversal, Hibernate would throw LazyInitializationException.
 */
@Service
@RequiredArgsConstructor
class AdminService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AirdropRepository airdropRepository;
    private final AirdropRecipientRepository recipientRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    AdminDashboardSummary dashboard() {
        long totalCompanies = companyRepository.count();
        long totalUsers = userRepository.count();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (AirdropStatus status : AirdropStatus.values()) {
            long count = airdropRepository.countByStatus(status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }

        long completed = recipientRepository.countByStatus(RecipientStatus.COMPLETED);
        long failed = recipientRepository.countByStatus(RecipientStatus.FAILED);

        var recentActivity = auditLogRepository.findAllByOrderByCreatedAtDesc(Pageable.ofSize(10))
                .map(this::toRecentAuditLog)
                .getContent();

        return new AdminDashboardSummary(totalCompanies, totalUsers, byStatus, completed, failed, recentActivity);
    }

    @Transactional(readOnly = true)
    Page<CompanySummary> listCompanies(Pageable pageable) {
        return companyRepository.findAll(pageable).map(c -> new CompanySummary(
                c.getId(), c.getName(), c.getEmail(), c.getStatus().name(),
                userRepository.countByCompanyId(c.getId()), airdropRepository.countByCompanyId(c.getId()), c.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    Page<AdminUserSummary> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(u -> new AdminUserSummary(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getRole().name(), u.getStatus().name(),
                u.getCompany() != null ? u.getCompany().getId() : null,
                u.getCompany() != null ? u.getCompany().getName() : null,
                u.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    Page<AdminAirdropSummary> listAirdrops(Pageable pageable) {
        return airdropRepository.findAll(pageable).map(a -> new AdminAirdropSummary(
                a.getId(), a.getName(), a.getStatus().name(), a.getCompany().getId(), a.getCompany().getName(),
                a.getTotalAmount(), a.getRecipientCount(), a.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    Page<AuditLogSummary> listAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toSummary);
    }

    private RecentAuditLog toRecentAuditLog(AuditLog log) {
        return new RecentAuditLog(log.getId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getUser() != null ? log.getUser().getEmail() : "system", log.getCreatedAt());
    }

    private AuditLogSummary toSummary(AuditLog log) {
        Company company = log.getCompany();
        return new AuditLogSummary(log.getId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getUser() != null ? log.getUser().getEmail() : "system",
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                log.getIpAddress(), log.getCreatedAt());
    }
}
