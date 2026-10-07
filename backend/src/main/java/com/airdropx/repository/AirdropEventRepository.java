package com.airdropx.repository;

import com.airdropx.model.AirdropEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AirdropEventRepository extends JpaRepository<AirdropEvent, UUID> {

    Page<AirdropEvent> findByAirdropIdOrderByCreatedAtAsc(UUID airdropId, Pageable pageable);
}
