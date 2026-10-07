package com.airdropx.common.enums;

/**
 * Airdrop lifecycle. Valid forward transitions (enforced in AirdropService, not just documented here):
 *
 *   DRAFT -> VALIDATING -> READY -> QUEUED -> RUNNING -> COMPLETED | PARTIALLY_COMPLETED | FAILED
 *     |         |            |         |
 *     +---------+------------+---------+--> CANCELLED  (allowed any time before RUNNING; not after)
 *
 * SCHEDULED exists in the schema for a future "launch at a specific time" feature — not used by v1 yet.
 */
public enum AirdropStatus {
    DRAFT, VALIDATING, READY, SCHEDULED, QUEUED, RUNNING, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED
}
