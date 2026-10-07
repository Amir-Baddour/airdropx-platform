package com.airdropx.user.airdrop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CreateAirdropRequest(
        @NotBlank String name,
        String description,
        @NotBlank String assetType
) {}

record RecipientInput(
        @NotBlank(message = "recipientAddress is required") String recipientAddress,
        @NotNull @DecimalMin(value = "0.00000001", message = "amount must be greater than 0") BigDecimal amount
) {}

record AddRecipientsRequest(
        @NotEmpty(message = "recipients must not be empty") List<@Valid RecipientInput> recipients
) {}

record RecipientResponse(
        UUID id, String recipientAddress, BigDecimal amount, String status,
        String errorMessage, Instant processedAt
) {}

record AirdropEventResponse(UUID id, String eventType, String message, Instant createdAt) {}

record AirdropResponse(
        UUID id, String name, String description, String assetType,
        BigDecimal totalAmount, int recipientCount, String status,
        Instant scheduledAt, Instant startedAt, Instant completedAt,
        Instant createdAt, Instant updatedAt
) {}

record LaunchResponse(UUID airdropId, UUID jobId, String status) {}
