package com.airdropx.repository;

import com.airdropx.model.AirdropTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AirdropTaskRepository extends JpaRepository<AirdropTask, UUID> {

    List<AirdropTask> findByAirdropIdOrderByPositionAscCreatedAtAsc(UUID airdropId);

    long countByAirdropId(UUID airdropId);

    Optional<AirdropTask> findByIdAndAirdropId(UUID id, UUID airdropId);
}
