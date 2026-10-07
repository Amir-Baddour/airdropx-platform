package com.airdropx.common.audit;

import com.airdropx.model.AuditLog;
import com.airdropx.model.Company;
import com.airdropx.model.User;
import com.airdropx.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /** For actions taken by an authenticated user within an HTTP request. */
    public void record(User actor, Company company, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        auditLogRepository.save(AuditLog.builder()
                .user(actor)
                .company(company)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(currentRequestIp())
                .metadata(metadata)
                .build());
    }

    /** For actions taken by the background worker — no HTTP request, no human actor. */
    public void recordSystem(Company company, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        auditLogRepository.save(AuditLog.builder()
                .user(null)
                .company(company)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(null)
                .metadata(metadata)
                .build());
    }

    private String currentRequestIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getRemoteAddr();
        } catch (IllegalStateException e) {
            // Not running inside an HTTP request thread — expected when called from the worker.
            return null;
        }
    }
}
