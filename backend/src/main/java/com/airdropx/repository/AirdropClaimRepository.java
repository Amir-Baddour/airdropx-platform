package com.airdropx.repository;

import com.airdropx.common.enums.ClaimStatus;
import com.airdropx.model.AirdropClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AirdropClaimRepository extends JpaRepository<AirdropClaim, UUID> {

    Page<AirdropClaim> findByAirdropId(UUID airdropId, Pageable pageable);

    Page<AirdropClaim> findByAirdropIdAndStatus(UUID airdropId, ClaimStatus status, Pageable pageable);

    Optional<AirdropClaim> findByIdAndAirdropId(UUID id, UUID airdropId);

    Optional<AirdropClaim> findByAirdropIdAndAddressKey(UUID airdropId, String addressKey);

    boolean existsByAirdropIdAndAddressKey(UUID airdropId, String addressKey);
}
