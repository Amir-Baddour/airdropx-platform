package com.airdropx.repository;

import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.model.Airdrop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AirdropRepository extends JpaRepository<Airdrop, UUID> {

    // Every user-facing lookup goes through the company_id filter — this is the tenant-isolation boundary.
    // Never expose a findById(UUID) path to user-facing code; always require the caller's companyId too.
    Optional<Airdrop> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<Airdrop> findByCompanyId(UUID companyId, Pageable pageable);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, AirdropStatus status);

    long countByStatus(AirdropStatus status);
}
