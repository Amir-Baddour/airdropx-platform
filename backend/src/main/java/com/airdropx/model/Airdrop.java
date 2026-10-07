package com.airdropx.model;

import com.airdropx.common.enums.AirdropStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "airdrops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Airdrop {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String assetType;

    @Column(nullable = false, precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private int recipientCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AirdropStatus status = AirdropStatus.DRAFT;

    // Claim system: while the airdrop is DRAFT and claimsOpen, recipients can apply via the public link.
    @Column(nullable = false)
    @Builder.Default
    private boolean claimsOpen = false;

    @Column(precision = 20, scale = 8)
    private BigDecimal claimAmount;

    private Instant scheduledAt;
    private Instant startedAt;
    private Instant completedAt;

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
