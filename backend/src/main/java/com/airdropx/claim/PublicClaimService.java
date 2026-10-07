package com.airdropx.claim;

import com.airdropx.common.audit.AuditLogService;
import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.common.enums.EventType;
import com.airdropx.common.exception.ApiException;
import com.airdropx.model.*;
import com.airdropx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The unauthenticated side of claiming. Everything here treats input as hostile: an airdrop that isn't
 * open for claims is reported as simply "not found" (no existence leak), proofs are length-limited,
 * and the one-claim-per-address rule is enforced both here and by a database unique constraint.
 */
@Service
@RequiredArgsConstructor
class PublicClaimService {

    private final AirdropRepository airdropRepository;
    private final AirdropTaskRepository taskRepository;
    private final AirdropClaimRepository claimRepository;
    private final ClaimSubmissionRepository submissionRepository;
    private final AirdropEventRepository eventRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    PublicAirdropResponse getOpenAirdrop(UUID airdropId) {
        Airdrop a = findOpen(airdropId);
        List<TaskResponse> tasks = taskRepository.findByAirdropIdOrderByPositionAscCreatedAtAsc(airdropId).stream()
                .map(t -> new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.isProofRequired()))
                .toList();
        return new PublicAirdropResponse(a.getId(), a.getName(), a.getDescription(), a.getAssetType(),
                a.getCompany().getName(), a.getClaimAmount(), tasks);
    }

    @Transactional
    ClaimStatusResponse submit(UUID airdropId, SubmitClaimRequest req) {
        Airdrop a = findOpen(airdropId);

        String address = req.address().trim();
        if (address.length() < 6 || address.contains(" ")) {
            throw ApiException.badRequest("INVALID_ADDRESS", "That doesn't look like a valid address (min 6 characters, no spaces)");
        }
        String key = keyOf(address);
        if (claimRepository.existsByAirdropIdAndAddressKey(airdropId, key)) {
            throw ApiException.conflict("ALREADY_CLAIMED", "A claim from this address already exists for this airdrop");
        }

        Map<UUID, AirdropTask> tasks = taskRepository.findByAirdropIdOrderByPositionAscCreatedAtAsc(airdropId).stream()
                .collect(Collectors.toMap(AirdropTask::getId, t -> t, (x, y) -> x, LinkedHashMap::new));

        Map<UUID, String> proofs = new HashMap<>();
        for (SubmissionInput in : req.submissions()) {
            if (!tasks.containsKey(in.taskId())) {
                throw ApiException.badRequest("UNKNOWN_TASK", "taskId " + in.taskId() + " does not belong to this airdrop");
            }
            if (proofs.put(in.taskId(), in.proof() == null ? "" : in.proof().trim()) != null) {
                throw ApiException.badRequest("DUPLICATE_TASK", "Each task can only be submitted once");
            }
        }
        for (AirdropTask t : tasks.values()) {
            String proof = proofs.get(t.getId());
            if (t.isProofRequired() && (proof == null || proof.isBlank())) {
                throw ApiException.badRequest("PROOF_REQUIRED", "Proof is required for task: " + t.getTitle());
            }
        }

        AirdropClaim claim = claimRepository.save(AirdropClaim.builder()
                .airdrop(a).claimantAddress(address).addressKey(key).build());

        List<ClaimSubmission> submissions = new ArrayList<>();
        proofs.forEach((taskId, proof) -> submissions.add(ClaimSubmission.builder()
                .claim(claim).task(tasks.get(taskId)).proofText(proof).build()));
        submissionRepository.saveAll(submissions);

        eventRepository.save(AirdropEvent.builder().airdrop(a).eventType(EventType.CLAIM_SUBMITTED)
                .message("New claim submitted").metadata(Map.of("claimId", claim.getId().toString())).build());
        auditLogService.record(null, a.getCompany(), "CLAIM_SUBMIT", "CLAIM", claim.getId(),
                Map.of("airdropId", airdropId.toString()));

        return new ClaimStatusResponse(claim.getId(), claim.getStatus().name(), null);
    }

    /**
     * Lets a claimant check their own result by address. Returns only status + the reviewer's note — never
     * other claimants' data. (Addresses aren't secret, so this reveals whether a given address has claimed;
     * an accepted trade-off for a status page that needs no login.)
     */
    @Transactional(readOnly = true)
    ClaimStatusResponse status(UUID airdropId, String address) {
        // Not gated on "open": claimants should still see their outcome after the window closes.
        airdropRepository.findById(airdropId).orElseThrow(() -> ApiException.notFound("Airdrop"));
        AirdropClaim c = claimRepository.findByAirdropIdAndAddressKey(airdropId, keyOf(address))
                .orElseThrow(() -> ApiException.notFound("Claim"));
        return new ClaimStatusResponse(c.getId(), c.getStatus().name(), c.getReviewNote());
    }

    private Airdrop findOpen(UUID airdropId) {
        return airdropRepository.findById(airdropId)
                .filter(a -> a.isClaimsOpen() && a.getStatus() == AirdropStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Airdrop"));
    }

    private static String keyOf(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }
}
