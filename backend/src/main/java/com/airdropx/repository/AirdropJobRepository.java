package com.airdropx.repository;

import com.airdropx.common.enums.JobStatus;
import com.airdropx.model.AirdropJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AirdropJobRepository extends JpaRepository<AirdropJob, UUID> {

    Optional<AirdropJob> findByAirdropId(UUID airdropId);

    // Used on worker startup to find jobs orphaned by a crash/restart mid-processing.
    List<AirdropJob> findByStatus(JobStatus status);
}
