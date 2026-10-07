package com.airdropx.repository;

import com.airdropx.model.ClaimSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClaimSubmissionRepository extends JpaRepository<ClaimSubmission, UUID> {

    List<ClaimSubmission> findByClaimIdIn(Collection<UUID> claimIds);

    long countByTaskId(UUID taskId);
}
