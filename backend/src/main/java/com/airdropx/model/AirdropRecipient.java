package com.airdropx.model;

import com.airdropx.common.enums.RecipientStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "airdrop_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirdropRecipient {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airdrop_id", nullable = false)
    private Airdrop airdrop;

    // Deliberately chain-agnostic: this is a mock distribution engine, not a real wallet integration.
    // See AirdropService.validateRecipientAddress() for the (intentionally light) format check.
    @Column(nullable = false, length = 255)
    private String recipientAddress;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecipientStatus status = RecipientStatus.PENDING;

    @Column(length = 255)
    private String externalReference;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Instant processedAt;

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
