package com.airdropx.repository;

import com.airdropx.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // Platform-wide, unscoped — only ever called from the admin module.
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
