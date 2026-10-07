package com.airdropx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Schema is in place for v1 but nothing writes to this table yet — see README > Roadmap
 * ("real CSV upload via Firebase Storage"). Kept so the table/entity shape is settled up front.
 */
@Entity
@Table(name = "file_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airdrop_id", nullable = false)
    private Airdrop airdrop;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 50)
    private String fileType;

    private Long fileSize;

    @Column(nullable = false, length = 500)
    private String firebasePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
