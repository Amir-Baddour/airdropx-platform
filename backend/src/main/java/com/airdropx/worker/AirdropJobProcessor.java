package com.airdropx.worker;

import com.airdropx.common.audit.AuditLogService;
import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.common.enums.EventType;
import com.airdropx.common.enums.JobStatus;
import com.airdropx.common.enums.RecipientStatus;
import com.airdropx.model.*;
import com.airdropx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Each method here is one committed unit of work, called by AirdropWorker across separate transactions
 * (deliberately — see AirdropWorker for why self-invocation inside one class wouldn't give that).
 * That's what makes crash recovery real: if the process dies mid-job, recipients already marked
 * COMPLETED/FAILED in earlier batches stay that way, and only the remaining PENDING ones get reprocessed.
 */
@Service
@RequiredArgsConstructor
class AirdropJobProcessor {

    private final AirdropJobRepository jobRepository;
    private final AirdropRepository airdropRepository;
    private final AirdropRecipientRepository recipientRepository;
    private final AirdropEventRepository eventRepository;
    private final AuditLogService auditLogService;

    @Value("${airdropx.worker.batch-size}")
    private int batchSize;

    // The one deliberately fake number in the system — simulates real-world delivery failures so
    // PARTIALLY_COMPLETED / FAILED paths actually fire in a demo instead of only the happy path.
    @Value("${airdropx.worker.simulated-failure-rate}")
    private double failureRate;

    /** Returns the airdrop id to process, or null if this job should be skipped (see comments below). */
    @Transactional
    UUID startJob(UUID jobId, String workerId) {
        AirdropJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.QUEUED) {
            return null; // stale/duplicate queue message, or already handled
        }

        Airdrop airdrop = job.getAirdrop();
        // QUEUED = normal pickup. RUNNING = crash recovery: the previous worker died after flipping the
        // airdrop to RUNNING, and recoverStuckJobs() requeued only the job row. Anything else (e.g. the
        // user cancelled between launch and pickup) is skipped.
        if (airdrop.getStatus() != AirdropStatus.QUEUED && airdrop.getStatus() != AirdropStatus.RUNNING) {
            return null;
        }

        job.setStatus(JobStatus.RUNNING);
        job.setWorkerId(workerId);
        job.setAttempts(job.getAttempts() + 1);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        airdrop.setStatus(AirdropStatus.RUNNING);
        airdrop.setStartedAt(Instant.now());
        airdropRepository.save(airdrop);

        writeEvent(airdrop, EventType.JOB_STARTED, "Worker picked up job (attempt " + job.getAttempts() + ")", null);
        return airdrop.getId();
    }

    /** Processes up to one batch of PENDING recipients. Returns true if more PENDING recipients remain. */
    @Transactional
    boolean processBatch(UUID jobId, UUID airdropId) {
        List<AirdropRecipient> batch = recipientRepository.findByAirdropIdAndStatusOrderByCreatedAtAsc(
                airdropId, RecipientStatus.PENDING, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return false;
        }

        for (AirdropRecipient r : batch) {
            boolean simulatedFailure = ThreadLocalRandom.current().nextDouble() < failureRate;
            r.setProcessedAt(Instant.now());
            if (simulatedFailure) {
                r.setStatus(RecipientStatus.FAILED);
                r.setErrorMessage("Simulated delivery failure — mock distribution engine, see README");
            } else {
                r.setStatus(RecipientStatus.COMPLETED);
                r.setExternalReference("MOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            }
        }
        recipientRepository.saveAll(batch);

        Airdrop airdrop = airdropRepository.findById(airdropId).orElseThrow();
        long processedSoFar = recipientRepository.countByAirdropIdAndStatus(airdropId, RecipientStatus.COMPLETED)
                + recipientRepository.countByAirdropIdAndStatus(airdropId, RecipientStatus.FAILED);
        int progress = airdrop.getRecipientCount() == 0
                ? 100 : (int) Math.round(100.0 * processedSoFar / airdrop.getRecipientCount());

        AirdropJob job = jobRepository.findById(jobId).orElseThrow();
        job.setProgress(progress);
        jobRepository.save(job);

        for (AirdropRecipient r : batch) {
            if (r.getStatus() == RecipientStatus.FAILED) {
                writeEvent(airdrop, EventType.RECIPIENT_FAILED, "Recipient " + r.getRecipientAddress() + " failed",
                        Map.of("recipientId", r.getId().toString()));
            }
        }
        writeEvent(airdrop, EventType.PROGRESS_UPDATED, progress + "% complete", Map.of("progress", progress));

        return recipientRepository.countByAirdropIdAndStatus(airdropId, RecipientStatus.PENDING) > 0;
    }

    @Transactional
    void finishJob(UUID jobId, UUID airdropId) {
        Airdrop airdrop = airdropRepository.findById(airdropId).orElseThrow();
        AirdropJob job = jobRepository.findById(jobId).orElseThrow();

        long completed = recipientRepository.countByAirdropIdAndStatus(airdropId, RecipientStatus.COMPLETED);
        long failed = recipientRepository.countByAirdropIdAndStatus(airdropId, RecipientStatus.FAILED);
        long total = airdrop.getRecipientCount();

        AirdropStatus finalStatus;
        if (failed == 0) {
            finalStatus = AirdropStatus.COMPLETED;
        } else if (completed == 0) {
            finalStatus = AirdropStatus.FAILED;
        } else {
            finalStatus = AirdropStatus.PARTIALLY_COMPLETED;
        }

        airdrop.setStatus(finalStatus);
        airdrop.setCompletedAt(Instant.now());
        airdropRepository.save(airdrop);

        job.setStatus(finalStatus == AirdropStatus.FAILED ? JobStatus.FAILED : JobStatus.COMPLETED);
        job.setProgress(100);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        writeEvent(airdrop, EventType.AIRDROP_COMPLETED,
                String.format("Finished: %d completed, %d failed of %d total", completed, failed, total),
                Map.of("completed", completed, "failed", failed, "total", total));

        auditLogService.recordSystem(airdrop.getCompany(), "AIRDROP_PROCESSING_FINISHED", "AIRDROP", airdrop.getId(),
                Map.of("status", finalStatus.name(), "completed", completed, "failed", failed));
    }

    /** Called when a job can't proceed at all — either an unexpected exception, or too many crash-recovery attempts. */
    @Transactional
    void markJobFailed(UUID jobId, String reason) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(reason);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            Airdrop airdrop = job.getAirdrop();
            airdrop.setStatus(AirdropStatus.FAILED);
            airdrop.setCompletedAt(Instant.now());
            airdropRepository.save(airdrop);

            writeEvent(airdrop, EventType.AIRDROP_COMPLETED, "Failed: " + reason, null);
        });
    }

    private void writeEvent(Airdrop airdrop, EventType type, String message, Map<String, Object> metadata) {
        eventRepository.save(AirdropEvent.builder().airdrop(airdrop).eventType(type).message(message).metadata(metadata).build());
    }
}
