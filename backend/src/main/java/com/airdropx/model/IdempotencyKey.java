package com.airdropx.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Mapped to DB column "key" — named keyValue here since `key` is a soft keyword best avoided as a Java field name.
    @Column(name = "key", nullable = false, length = 255)
    private String keyValue;

    @Column(nullable = false, length = 100)
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String requestHash;

    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> responseBody;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
