package com.airdropx.model;

import com.airdropx.common.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "airdrop_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirdropJob {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airdrop_id", nullable = false)
    private Airdrop airdrop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    @Column(length = 50)
    private String workerId;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(nullable = false)
    @Builder.Default
    private int progress = 0;

    private Instant startedAt;
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
