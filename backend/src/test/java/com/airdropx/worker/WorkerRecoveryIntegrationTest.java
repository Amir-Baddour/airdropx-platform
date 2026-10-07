package com.airdropx.worker;

import com.airdropx.AbstractIntegrationTest;
import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.common.enums.JobStatus;
import com.airdropx.common.enums.RecipientStatus;
import com.airdropx.model.Airdrop;
import com.airdropx.model.AirdropJob;
import com.airdropx.model.AirdropRecipient;
import com.airdropx.repository.AirdropJobRepository;
import com.airdropx.repository.AirdropRecipientRepository;
import com.airdropx.repository.AirdropRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Simulates "the process died mid-job": we hand-craft the DB state a crashed worker would leave behind
 * (job RUNNING, airdrop RUNNING, some recipients already paid), then invoke the worker's startup routine
 * exactly as Spring does on boot.
 */
class WorkerRecoveryIntegrationTest extends AbstractIntegrationTest {

    @Autowired AirdropWorker worker;
    @Autowired AirdropRepository airdropRepository;
    @Autowired AirdropJobRepository jobRepository;
    @Autowired AirdropRecipientRepository recipientRepository;

    private AirdropJob crashedJob(UUID airdropId, int attempts) {
        Airdrop airdrop = airdropRepository.findById(airdropId).orElseThrow();
        airdrop.setStatus(AirdropStatus.RUNNING);
        airdropRepository.save(airdrop);
        return jobRepository.save(AirdropJob.builder()
                .airdrop(airdrop).status(JobStatus.RUNNING).workerId("dead-worker").attempts(attempts).progress(40).build());
    }

    @Test
    @DisplayName("a job orphaned in RUNNING is requeued on startup and finished; already-paid recipients are not paid twice")
    void resumesOrphanedJob() throws Exception {
        Tenant t = registerTenant("recover");
        UUID id = readyAirdrop(t, 4);

        // Pretend the first two recipients were paid before the crash.
        var recipients = recipientRepository.findByAirdropIdAndStatus(id, RecipientStatus.PENDING);
        for (int i = 0; i < 2; i++) {
            AirdropRecipient r = recipients.get(i);
            r.setStatus(RecipientStatus.COMPLETED);
            r.setExternalReference("MOCK-BEFORE-CRASH-" + i);
            recipientRepository.save(r);
        }
        AirdropJob job = crashedJob(id, 1);

        worker.run(new DefaultApplicationArguments()); // what Spring does at boot

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(airdropStatus(t, id)).isEqualTo("COMPLETED"));

        assertThat(recipientRepository.countByAirdropIdAndStatus(id, RecipientStatus.COMPLETED)).isEqualTo(4);
        long preserved = recipientRepository.findByAirdropId(id, org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(r -> r.getExternalReference() != null
                        && r.getExternalReference().startsWith("MOCK-BEFORE-CRASH")).count();
        assertThat(preserved).isEqualTo(2);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("a job that already burned all its attempts is marked FAILED instead of looping forever")
    void givesUpAfterMaxAttempts() throws Exception {
        Tenant t = registerTenant("giveup");
        UUID id = readyAirdrop(t, 2);
        AirdropJob job = crashedJob(id, 3); // max-attempts is 3

        worker.run(new DefaultApplicationArguments());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.FAILED));
        assertThat(airdropStatus(t, id)).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("a stale queue message for a cancelled airdrop is ignored by the worker")
    void skipsCancelledBeforePickup() throws Exception {
        Tenant t = registerTenant("stale");
        UUID id = readyAirdrop(t, 1);
        postJson("/api/v1/user/airdrops/" + id + "/cancel", t.accessToken(), null, 200);

        Airdrop airdrop = airdropRepository.findById(id).orElseThrow();
        AirdropJob job = jobRepository.save(AirdropJob.builder().airdrop(airdrop).status(JobStatus.QUEUED).build());

        // Drive the processor directly: deterministic, no sleeping for a queue consumer.
        UUID result = processor.startJob(job.getId(), "test-worker");
        assertThat(result).isNull();
        assertThat(airdropStatus(t, id)).isEqualTo("CANCELLED");
    }

    @Autowired AirdropJobProcessor processor;
}
