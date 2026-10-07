package com.airdropx.claim;

import com.airdropx.common.audit.AuditLogService;
import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.common.enums.ClaimStatus;
import com.airdropx.common.enums.EventType;
import com.airdropx.common.exception.ApiException;
import com.airdropx.model.*;
import com.airdropx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Company-side claim management: configure tasks, open/close claiming, review claims. */
@Service
@RequiredArgsConstructor
class ClaimAdminService {

    private final AirdropRepository airdropRepository;
    private final AirdropTaskRepository taskRepository;
    private final AirdropClaimRepository claimRepository;
    private final ClaimSubmissionRepository submissionRepository;
    private final AirdropRecipientRepository recipientRepository;
    private final AirdropEventRepository eventRepository;
    private final AuditLogService auditLogService;

    // --- Settings ------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    ClaimSettingsResponse getSettings(UUID companyId, UUID airdropId) {
        Airdrop a = findOwned(companyId, airdropId);
        return toSettings(a);
    }

    @Transactional
    ClaimSettingsResponse updateSettings(User actor, UUID companyId, UUID airdropId, ClaimSettingsRequest req) {
        Airdrop a = findOwned(companyId, airdropId);
        requireDraft(a, "change claim settings for");

        if (req.claimAmount() != null && req.claimAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("INVALID_CLAIM_AMOUNT", "claimAmount must be greater than 0");
        }
        if (req.claimsOpen()) {
            if (req.claimAmount() == null) {
                throw ApiException.badRequest("CLAIM_AMOUNT_REQUIRED", "Set the amount each approved claimant receives before opening claims");
            }
            if (taskRepository.countByAirdropId(airdropId) == 0) {
                throw ApiException.badRequest("NO_TASKS", "Add at least one task before opening claims");
            }
        }

        a.setClaimAmount(req.claimAmount());
        a.setClaimsOpen(req.claimsOpen());
        airdropRepository.save(a);

        auditLogService.record(actor, a.getCompany(), "CLAIM_SETTINGS_UPDATE", "AIRDROP", a.getId(),
                Map.of("claimsOpen", req.claimsOpen()));
        return toSettings(a);
    }

    // --- Tasks -----------------------------------------------------------------------------------------

    @Transactional
    TaskResponse addTask(User actor, UUID companyId, UUID airdropId, TaskRequest req) {
        Airdrop a = findOwned(companyId, airdropId);
        requireDraft(a, "add tasks to");

        AirdropTask task = taskRepository.save(AirdropTask.builder()
                .airdrop(a)
                .title(req.title().trim())
                .description(req.description())
                .proofRequired(req.proofRequired() == null || req.proofRequired())
                .position((int) taskRepository.countByAirdropId(airdropId))
                .build());

        auditLogService.record(actor, a.getCompany(), "CLAIM_TASK_ADD", "AIRDROP", a.getId(),
                Map.of("taskId", task.getId().toString()));
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    List<TaskResponse> listTasks(UUID companyId, UUID airdropId) {
        findOwned(companyId, airdropId);
        return taskRepository.findByAirdropIdOrderByPositionAscCreatedAtAsc(airdropId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    void deleteTask(User actor, UUID companyId, UUID airdropId, UUID taskId) {
        Airdrop a = findOwned(companyId, airdropId);
        requireDraft(a, "remove tasks from");
        AirdropTask task = taskRepository.findByIdAndAirdropId(taskId, airdropId)
                .orElseThrow(() -> ApiException.notFound("Task"));
        if (submissionRepository.countByTaskId(taskId) > 0) {
            throw ApiException.conflict("TASK_HAS_SUBMISSIONS", "Claims already reference this task, so it can't be removed");
        }
        taskRepository.delete(task);
        auditLogService.record(actor, a.getCompany(), "CLAIM_TASK_REMOVE", "AIRDROP", a.getId(),
                Map.of("taskId", taskId.toString()));
    }

    // --- Review ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Page<ClaimResponse> listClaims(UUID companyId, UUID airdropId, ClaimStatus status, Pageable pageable) {
        findOwned(companyId, airdropId);
        Page<AirdropClaim> page = status == null
                ? claimRepository.findByAirdropId(airdropId, pageable)
                : claimRepository.findByAirdropIdAndStatus(airdropId, status, pageable);

        List<UUID> ids = page.getContent().stream().map(AirdropClaim::getId).toList();
        Map<UUID, List<ClaimSubmission>> byClaim = ids.isEmpty() ? Map.of()
                : submissionRepository.findByClaimIdIn(ids).stream()
                        .collect(Collectors.groupingBy(s -> s.getClaim().getId()));

        return page.map(c -> toResponse(c, byClaim.getOrDefault(c.getId(), List.of())));
    }

    /**
     * Approving turns the claimant into a normal airdrop recipient, so everything downstream (validate,
     * launch, worker, idempotency, progress) is the existing, tested pipeline — the claim system only
     * decides WHO gets into the recipient list.
     */
    @Transactional
    ClaimResponse approve(User actor, UUID companyId, UUID airdropId, UUID claimId) {
        Airdrop a = findOwned(companyId, airdropId);
        requireDraft(a, "approve claims on");
        AirdropClaim claim = findClaim(airdropId, claimId);
        requirePending(claim);

        if (a.getClaimAmount() == null) {
            throw ApiException.badRequest("CLAIM_AMOUNT_REQUIRED", "Set the claim amount before approving claims");
        }
        if (recipientRepository.existsByAirdropIdAndRecipientAddressIgnoreCase(airdropId, claim.getClaimantAddress())) {
            throw ApiException.conflict("ALREADY_A_RECIPIENT", "This address is already in the recipient list");
        }

        recipientRepository.save(AirdropRecipient.builder()
                .airdrop(a).recipientAddress(claim.getClaimantAddress()).amount(a.getClaimAmount()).build());
        a.setTotalAmount(recipientRepository.sumAmountByAirdropId(airdropId));
        a.setRecipientCount((int) recipientRepository.countByAirdropId(airdropId));
        airdropRepository.save(a);

        claim.setStatus(ClaimStatus.APPROVED);
        claim.setReviewedBy(actor);
        claim.setReviewedAt(Instant.now());
        claimRepository.save(claim);

        writeEvent(a, EventType.CLAIM_APPROVED, "Claim approved for " + claim.getClaimantAddress(),
                Map.of("claimId", claimId.toString()));
        auditLogService.record(actor, a.getCompany(), "CLAIM_APPROVE", "CLAIM", claimId, Map.of("airdropId", airdropId.toString()));

        return toResponse(claim, submissionRepository.findByClaimIdIn(List.of(claimId)));
    }

    @Transactional
    ClaimResponse reject(User actor, UUID companyId, UUID airdropId, UUID claimId, String note) {
        Airdrop a = findOwned(companyId, airdropId);
        AirdropClaim claim = findClaim(airdropId, claimId);
        requirePending(claim);

        claim.setStatus(ClaimStatus.REJECTED);
        claim.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        claim.setReviewedBy(actor);
        claim.setReviewedAt(Instant.now());
        claimRepository.save(claim);

        writeEvent(a, EventType.CLAIM_REJECTED, "Claim rejected for " + claim.getClaimantAddress(),
                Map.of("claimId", claimId.toString()));
        auditLogService.record(actor, a.getCompany(), "CLAIM_REJECT", "CLAIM", claimId, Map.of("airdropId", airdropId.toString()));

        return toResponse(claim, submissionRepository.findByClaimIdIn(List.of(claimId)));
    }

    // --- Helpers -------------------------------------------------------------------------------------------

    private Airdrop findOwned(UUID companyId, UUID airdropId) {
        return airdropRepository.findByIdAndCompanyId(airdropId, companyId)
                .orElseThrow(() -> ApiException.notFound("Airdrop"));
    }

    private AirdropClaim findClaim(UUID airdropId, UUID claimId) {
        return claimRepository.findByIdAndAirdropId(claimId, airdropId)
                .orElseThrow(() -> ApiException.notFound("Claim"));
    }

    private void requireDraft(Airdrop a, String action) {
        if (a.getStatus() != AirdropStatus.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Cannot " + action + " an airdrop in " + a.getStatus() + " status (expected DRAFT)");
        }
    }

    private void requirePending(AirdropClaim claim) {
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw ApiException.conflict("CLAIM_ALREADY_REVIEWED", "This claim was already " + claim.getStatus().name().toLowerCase());
        }
    }

    private void writeEvent(Airdrop a, EventType type, String message, Map<String, Object> metadata) {
        eventRepository.save(AirdropEvent.builder().airdrop(a).eventType(type).message(message).metadata(metadata).build());
    }

    private ClaimSettingsResponse toSettings(Airdrop a) {
        return new ClaimSettingsResponse(a.isClaimsOpen(), a.getClaimAmount(), (int) taskRepository.countByAirdropId(a.getId()));
    }

    private TaskResponse toResponse(AirdropTask t) {
        return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.isProofRequired());
    }

    private ClaimResponse toResponse(AirdropClaim c, List<ClaimSubmission> submissions) {
        List<SubmissionResponse> subs = submissions.stream()
                .map(s -> new SubmissionResponse(s.getTask().getId(), s.getTask().getTitle(), s.getProofText()))
                .toList();
        return new ClaimResponse(c.getId(), c.getClaimantAddress(), c.getStatus().name(), c.getReviewNote(),
                c.getCreatedAt(), c.getReviewedAt(), subs);
    }
}
