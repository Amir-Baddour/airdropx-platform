package com.airdropx.worker;

import com.airdropx.common.enums.JobStatus;
import com.airdropx.model.AirdropJob;
import com.airdropx.repository.AirdropJobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Stands in for the "Worker Containers (Docker)" in the architecture diagram. In v1 this is a single
 * in-process daemon thread rather than a separately deployed/scaled container — see README > "Adaptations
 * from the diagrams" for why, and for how this would split out into real worker containers later.
 *
 * Runs on application startup as a background thread (started from an ApplicationRunner so it never
 * blocks Spring's own startup) and does two things:
 *   1. On boot, reconciles any job left in RUNNING status — meaning the previous process died mid-job —
 *      by requeueing it (up to max-attempts) or marking it FAILED. This is the "worker crash/retry" edge
 *      case from the ERD, made real: kill `docker compose restart backend` mid-airdrop and watch it resume.
 *   2. Blocks on Redis (BRPOP-style, via a timeout so the loop can still notice shutdown) for job ids
 *      pushed by AirdropService.launch(), and hands each one to AirdropJobProcessor.
 */
@Component
@RequiredArgsConstructor
public class AirdropWorker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AirdropWorker.class);
    private static final String WORKER_ID = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    private final StringRedisTemplate redisTemplate;
    private final AirdropJobRepository jobRepository;
    private final AirdropJobProcessor processor;

    @Value("${airdropx.worker.queue-key}")
    private String queueKey;

    @Value("${airdropx.worker.simulated-batch-delay-ms}")
    private long batchDelayMs;

    @Value("${airdropx.worker.max-attempts}")
    private int maxAttempts;

    @Override
    public void run(ApplicationArguments args) {
        recoverStuckJobs();

        Thread workerThread = new Thread(this::consumeLoop, "airdrop-worker-" + WORKER_ID);
        workerThread.setDaemon(true);
        workerThread.start();

        log.info("AirdropWorker [{}] started, watching Redis list '{}'", WORKER_ID, queueKey);
    }

    private void recoverStuckJobs() {
        List<AirdropJob> orphaned = jobRepository.findByStatus(JobStatus.RUNNING);
        for (AirdropJob job : orphaned) {
            if (job.getAttempts() < maxAttempts) {
                log.warn("Recovering orphaned job {} (attempt {} of {}) — requeueing after restart",
                        job.getId(), job.getAttempts(), maxAttempts);
                job.setStatus(JobStatus.QUEUED);
                jobRepository.save(job);
                redisTemplate.opsForList().leftPush(queueKey, job.getId().toString());
            } else {
                log.warn("Job {} exceeded max attempts ({}) after restart — marking failed", job.getId(), maxAttempts);
                processor.markJobFailed(job.getId(), "Exceeded max attempts (" + maxAttempts + ") across worker restarts");
            }
        }
    }

    private void consumeLoop() {
        while (true) {
            try {
                // Blocks up to 5s waiting for a job id; returns null on timeout so the loop can keep living
                // (and would eventually check a shutdown flag here in a version that supports graceful stop).
                String jobId = redisTemplate.opsForList().rightPop(queueKey, Duration.ofSeconds(5));
                if (jobId != null) {
                    processJob(UUID.fromString(jobId));
                }
            } catch (Exception e) {
                // A malformed message or a transient Redis/DB blip should never kill the loop — log and keep going.
                log.error("Worker loop error, continuing", e);
                sleepQuietly(1000);
            }
        }
    }

    private void processJob(UUID jobId) {
        UUID airdropId = processor.startJob(jobId, WORKER_ID);
        if (airdropId == null) {
            log.debug("Job {} skipped (not found, already handled, or cancelled before pickup)", jobId);
            return;
        }

        try {
            boolean hasMore = true;
            while (hasMore) {
                sleepQuietly(batchDelayMs); // simulates real distribution work taking time, batch by batch
                hasMore = processor.processBatch(jobId, airdropId);
            }
            processor.finishJob(jobId, airdropId);
            log.info("Job {} finished", jobId);
        } catch (Exception e) {
            log.error("Job {} failed unexpectedly", jobId, e);
            processor.markJobFailed(jobId, "Unexpected error: " + e.getMessage());
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
