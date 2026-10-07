package com.airdropx.claim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// ---- company side ------------------------------------------------------------------------------

record ClaimSettingsRequest(boolean claimsOpen, BigDecimal claimAmount) {}

record ClaimSettingsResponse(boolean claimsOpen, BigDecimal claimAmount, int taskCount) {}

record TaskRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 1000) String description,
        Boolean proofRequired
) {}

record TaskResponse(UUID id, String title, String description, boolean proofRequired) {}

record SubmissionResponse(UUID taskId, String taskTitle, String proof) {}

record ClaimResponse(
        UUID id, String claimantAddress, String status, String reviewNote,
        Instant createdAt, Instant reviewedAt, List<SubmissionResponse> submissions
) {}

record RejectRequest(@Size(max = 500) String note) {}

// ---- public side -------------------------------------------------------------------------------

record PublicAirdropResponse(
        UUID id, String name, String description, String assetType, String companyName,
        BigDecimal claimAmount, List<TaskResponse> tasks
) {}

record SubmissionInput(@NotNull UUID taskId, @Size(max = 1000) String proof) {}

record SubmitClaimRequest(
        @NotBlank @Size(max = 255) String address,
        @NotNull List<@Valid SubmissionInput> submissions
) {}

record ClaimStatusResponse(UUID claimId, String status, String reviewNote) {}
