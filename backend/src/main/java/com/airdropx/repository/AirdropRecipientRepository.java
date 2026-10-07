package com.airdropx.repository;

import com.airdropx.common.enums.RecipientStatus;
import com.airdropx.model.AirdropRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AirdropRecipientRepository extends JpaRepository<AirdropRecipient, UUID> {

    Page<AirdropRecipient> findByAirdropId(UUID airdropId, Pageable pageable);

    List<AirdropRecipient> findByAirdropIdAndStatus(UUID airdropId, RecipientStatus status);

    long countByAirdropIdAndStatus(UUID airdropId, RecipientStatus status);

    @Query("select coalesce(sum(r.amount), 0) from AirdropRecipient r where r.airdrop.id = :airdropId")
    BigDecimal sumAmountByAirdropId(@Param("airdropId") UUID airdropId);

    long countByAirdropId(UUID airdropId);

    boolean existsByAirdropIdAndRecipientAddressIgnoreCase(UUID airdropId, String recipientAddress);

    // Fetches one batch of PENDING recipients for the worker to process next. Ordered by creation so
    // recipients are processed in the order they were added (keeps demo runs deterministic). The caller
    // passes PageRequest.of(0, batchSize) — standard Spring Data pagination, not a JPQL LIMIT clause,
    // since a hand-written LIMIT here isn't something I can compile-verify in this sandbox.
    List<AirdropRecipient> findByAirdropIdAndStatusOrderByCreatedAtAsc(UUID airdropId, RecipientStatus status, Pageable pageable);

    // Nested-property traversal (airdrop -> company -> id) via Spring Data's underscore syntax — used by
    // the company-scoped dashboard so it can total recipient outcomes with one query per status instead
    // of looping over every airdrop in the company.
    long countByAirdrop_Company_IdAndStatus(UUID companyId, RecipientStatus status);

    // Platform-wide, unscoped — used only by the admin dashboard.
    long countByStatus(RecipientStatus status);
}
