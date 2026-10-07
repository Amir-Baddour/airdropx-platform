package com.airdropx.user.airdrop;

import com.airdropx.common.audit.AuditLogService;
import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.common.enums.EventType;
import com.airdropx.common.enums.JobStatus;
import com.airdropx.common.exception.ApiException;
import com.airdropx.model.*;
import com.airdropx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AirdropService {

    private final AirdropRepository airdropRepository;
    private final AirdropRecipientRepository recipientRepository;
    private final AirdropJobRepository jobRepository;
    private final AirdropEventRepository eventRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;
    private final StringRedisTemplate redisTemplate;

    @Value("${airdropx.worker.queue-key}")
    private String queueKey;

    private static final Set<AirdropStatus> CANCELLABLE = EnumSet.of(
            AirdropStatus.DRAFT, AirdropStatus.VALIDATING, AirdropStatus.READY, AirdropStatus.QUEUED);

    // --- Create -------------------------------------------------------------------------------

    @Transactional
    AirdropResponse create(User actor, UUID companyId, CreateAirdropRequest req) {
        Company company = companyRepository.getReferenceById(companyId);

        Airdrop airdrop = airdropRepository.save(Airdrop.builder()
                .company(company)
                .createdBy(actor)
                .name(req.name())
                .description(req.description())
                .assetType(req.assetType())
                .status(AirdropStatus.DRAFT)
                .build());

        writeEvent(airdrop, EventType.AIRDROP_CREATED, "Airdrop created as draft", null);
        auditLogService.record(actor, company, "AIRDROP_CREATE", "AIRDROP", airdrop.getId(), Map.of("name", req.name()));

        return toResponse(airdrop);
    }

    // --- Read -----------------------------------------------------------------------------------

    Page<AirdropResponse> list(UUID companyId, Pageable pageable) {
        return airdropRepository.findByCompanyId(companyId, pageable).map(this::toResponse);
    }

    AirdropResponse get(UUID companyId, UUID airdropId) {
        return toResponse(findOwned(companyId, airdropId));
    }

    Page<RecipientResponse> listRecipients(UUID companyId, UUID airdropId, Pageable pageable) {
        findOwned(companyId, airdropId); // tenant check
        return recipientRepository.findByAirdropId(airdropId, pageable).map(this::toResponse);
    }

    Page<AirdropEventResponse> listEvents(UUID companyId, UUID airdropId, Pageable pageable) {
        findOwned(companyId, airdropId); // tenant check
        return eventRepository.findByAirdropIdOrderByCreatedAtAsc(airdropId, pageable)
                .map(e -> new AirdropEventResponse(e.getId(), e.getEventType().name(), e.getMessage(), e.getCreatedAt()));
    }

    // --- Recipients -------------------------------------------------------------------------------

    @Transactional
    AirdropResponse addRecipients(User actor, UUID companyId, UUID airdropId, AddRecipientsRequest req) {
        Airdrop airdrop = findOwned(companyId, airdropId);
        requireStatus(airdrop, AirdropStatus.DRAFT, "add recipients to");

        for (RecipientInput input : req.recipients()) {
            if (!isPlausibleAddress(input.recipientAddress())) {
                throw ApiException.badRequest("INVALID_RECIPIENT_ADDRESS",
                        "\"" + input.recipientAddress() + "\" doesn't look like a valid recipient address");
            }
        }

        List<AirdropRecipient> saved = req.recipients().stream()
                .map(r -> AirdropRecipient.builder().airdrop(airdrop).recipientAddress(r.recipientAddress()).amount(r.amount()).build())
                .toList();
        recipientRepository.saveAll(saved);

        recomputeTotals(airdrop);

        auditLogService.record(actor, airdrop.getCompany(), "AIRDROP_ADD_RECIPIENTS", "AIRDROP", airdrop.getId(),
                Map.of("added", saved.size()));

        return toResponse(airdrop);
    }

    // --- Validate -------------------------------------------------------------------------------

    @Transactional
    AirdropResponse validate(User actor, UUID companyId, UUID airdropId) {
        Airdrop airdrop = findOwned(companyId, airdropId);
        requireStatus(airdrop, AirdropStatus.DRAFT, "validate");

        writeEvent(airdrop, EventType.VALIDATION_STARTED, "Validation started", null);

        if (airdrop.getRecipientCount() == 0) {
            throw ApiException.badRequest("NO_RECIPIENTS", "Add at least one recipient before validating");
        }
        if (airdrop.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("ZERO_TOTAL_AMOUNT", "Total amount must be greater than zero");
        }

        airdrop.setStatus(AirdropStatus.READY);
        airdropRepository.save(airdrop);
        writeEvent(airdrop, EventType.VALIDATION_COMPLETED, "Validation passed — ready to launch", Map.of(
                "recipientCount", airdrop.getRecipientCount(), "totalAmount", airdrop.getTotalAmount().toPlainString()));

        auditLogService.record(actor, airdrop.getCompany(), "AIRDROP_VALIDATE", "AIRDROP", airdrop.getId(), null);
        return toResponse(airdrop);
    }

    // --- Launch (wrapped in IdempotencyService by the controller) -------------------------------

    @Transactional
    LaunchResponse launch(User actor, UUID companyId, UUID airdropId) {
        Airdrop airdrop = findOwned(companyId, airdropId);
        requireStatus(airdrop, AirdropStatus.READY, "launch");

        AirdropJob job = jobRepository.save(AirdropJob.builder().airdrop(airdrop).status(JobStatus.QUEUED).build());

        airdrop.setStatus(AirdropStatus.QUEUED);
        airdropRepository.save(airdrop);
        writeEvent(airdrop, EventType.JOB_CREATED, "Queued for processing", Map.of("jobId", job.getId().toString()));

        auditLogService.record(actor, airdrop.getCompany(), "AIRDROP_LAUNCH", "AIRDROP", airdrop.getId(),
                Map.of("jobId", job.getId().toString()));

        // Push to Redis only AFTER the DB transaction commits. Pushing inside the transaction lets the worker
        // pop the id before the QUEUED row is visible, find nothing, and silently drop the job (job stuck QUEUED).
        // If the transaction rolls back, afterCommit never fires, so a rolled-back launch can't be resurrected.
        final String jobIdStr = job.getId().toString();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.opsForList().leftPush(queueKey, jobIdStr);
            }
        });

        return new LaunchResponse(airdrop.getId(), job.getId(), airdrop.getStatus().name());
    }

    // --- Cancel -----------------------------------------------------------------------------------

    @Transactional
    AirdropResponse cancel(User actor, UUID companyId, UUID airdropId) {
        Airdrop airdrop = findOwned(companyId, airdropId);

        if (!CANCELLABLE.contains(airdrop.getStatus())) {
            // This is the "cancel while running" edge case from the ERD, made real: once a worker has
            // picked the job up, we don't allow a cancel to race with in-flight recipient processing.
            throw ApiException.conflict("CANNOT_CANCEL",
                    "Cannot cancel an airdrop in " + airdrop.getStatus() + " status");
        }

        airdrop.setStatus(AirdropStatus.CANCELLED);
        airdropRepository.save(airdrop);
        writeEvent(airdrop, EventType.AIRDROP_CANCELLED, "Cancelled by " + actor.getEmail(), null);

        auditLogService.record(actor, airdrop.getCompany(), "AIRDROP_CANCEL", "AIRDROP", airdrop.getId(), null);
        return toResponse(airdrop);
    }

    // --- Helpers -----------------------------------------------------------------------------------

    private Airdrop findOwned(UUID companyId, UUID airdropId) {
        return airdropRepository.findByIdAndCompanyId(airdropId, companyId)
                .orElseThrow(() -> ApiException.notFound("Airdrop"));
    }

    private void requireStatus(Airdrop airdrop, AirdropStatus expected, String action) {
        if (airdrop.getStatus() != expected) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Cannot " + action + " an airdrop in " + airdrop.getStatus() + " status (expected " + expected + ")");
        }
    }

    private void recomputeTotals(Airdrop airdrop) {
        airdrop.setTotalAmount(recipientRepository.sumAmountByAirdropId(airdrop.getId()));
        airdrop.setRecipientCount((int) recipientRepository.countByAirdropId(airdrop.getId()));
        airdropRepository.save(airdrop);
    }

    /**
     * Deliberately light-touch: this is a mock distribution engine, not a real wallet/chain integration,
     * so there's no chain to validate an address format against. Real validation (checksum, chain-specific
     * format) is exactly the kind of thing that becomes meaningful once a real chain is chosen — see
     * README > Roadmap.
     */
    private boolean isPlausibleAddress(String address) {
        String trimmed = address.trim();
        return trimmed.length() >= 6 && trimmed.length() <= 255 && !trimmed.contains(" ");
    }

    private void writeEvent(Airdrop airdrop, EventType type, String message, Map<String, Object> metadata) {
        eventRepository.save(AirdropEvent.builder().airdrop(airdrop).eventType(type).message(message).metadata(metadata).build());
    }

    private AirdropResponse toResponse(Airdrop a) {
        return new AirdropResponse(a.getId(), a.getName(), a.getDescription(), a.getAssetType(),
                a.getTotalAmount(), a.getRecipientCount(), a.getStatus().name(),
                a.getScheduledAt(), a.getStartedAt(), a.getCompletedAt(), a.getCreatedAt(), a.getUpdatedAt());
    }

    private RecipientResponse toResponse(AirdropRecipient r) {
        return new RecipientResponse(r.getId(), r.getRecipientAddress(), r.getAmount(),
                r.getStatus().name(), r.getErrorMessage(), r.getProcessedAt());
    }
}
