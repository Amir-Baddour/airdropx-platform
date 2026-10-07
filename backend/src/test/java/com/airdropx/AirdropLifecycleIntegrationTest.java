package com.airdropx;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The full create → recipients → validate → launch → worker → completed flow, plus its guard rails. */
class AirdropLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    private void awaitStatus(Tenant t, UUID id, String expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                assertThat(airdropStatus(t, id)).isEqualTo(expected));
    }

    @Test
    @DisplayName("happy path: launched airdrop is processed by the worker and ends COMPLETED with every recipient paid")
    void happyPath() throws Exception {
        Tenant t = registerTenant("happy");
        UUID id = readyAirdrop(t, 5);

        JsonNode launched = launch(t, id, "key-" + UUID.randomUUID(), 202);
        assertThat(launched.get("status").asText()).isEqualTo("QUEUED");

        // Regression: job used to get stuck in QUEUED because the Redis push happened before the DB commit.
        awaitStatus(t, id, "COMPLETED");

        JsonNode recipients = getJson("/api/v1/user/airdrops/" + id + "/recipients", t.accessToken(), 200);
        assertThat(recipients.get("totalElements").asInt()).isEqualTo(5);
        for (JsonNode r : recipients.get("items")) {
            assertThat(r.get("status").asText()).isEqualTo("COMPLETED");
        }

        JsonNode events = getJson("/api/v1/user/airdrops/" + id + "/events", t.accessToken(), 200);
        List<String> types = events.get("items").findValuesAsText("eventType");
        assertThat(types).contains("AIRDROP_CREATED", "VALIDATION_COMPLETED", "JOB_CREATED", "JOB_STARTED",
                "PROGRESS_UPDATED", "AIRDROP_COMPLETED");

        Integer progress = jdbc.queryForObject("SELECT progress FROM airdrop_jobs WHERE airdrop_id = ?", Integer.class, id);
        assertThat(progress).isEqualTo(100);
    }

    @Test
    @DisplayName("replaying a launch with the same Idempotency-Key returns the same job and creates only one")
    void launchIsIdempotent() throws Exception {
        Tenant t = registerTenant("idem");
        UUID id = readyAirdrop(t, 2);
        String key = "key-" + UUID.randomUUID();

        JsonNode first = launch(t, id, key, 202);
        JsonNode replay = launch(t, id, key, 202);

        assertThat(replay.get("jobId").asText()).isEqualTo(first.get("jobId").asText());
        Integer jobs = jdbc.queryForObject("SELECT count(*) FROM airdrop_jobs WHERE airdrop_id = ?", Integer.class, id);
        assertThat(jobs).isEqualTo(1);
        awaitStatus(t, id, "COMPLETED");
    }

    @Test
    @DisplayName("reusing an Idempotency-Key for a different airdrop is rejected with 409")
    void idempotencyKeyReuseAcrossRequests() throws Exception {
        Tenant t = registerTenant("reuse");
        UUID one = readyAirdrop(t, 1);
        UUID two = readyAirdrop(t, 1);
        String key = "key-" + UUID.randomUUID();

        launch(t, one, key, 202);
        JsonNode err = launch(t, two, key, 409);
        assertThat(err.get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(airdropStatus(t, two)).isEqualTo("READY");
    }

    @Test
    @DisplayName("launch without an Idempotency-Key is a 400")
    void launchRequiresKey() throws Exception {
        Tenant t = registerTenant("nokey");
        UUID id = readyAirdrop(t, 1);
        launch(t, id, null, 400);
        assertThat(airdropStatus(t, id)).isEqualTo("READY");
    }

    @Test
    @DisplayName("a second launch with a NEW key is rejected once the airdrop has already left READY")
    void doubleLaunchWithDifferentKeys() throws Exception {
        Tenant t = registerTenant("double");
        UUID id = readyAirdrop(t, 1);
        launch(t, id, "k1-" + UUID.randomUUID(), 202);
        JsonNode err = launch(t, id, "k2-" + UUID.randomUUID(), 409);
        assertThat(err.get("code").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
        awaitStatus(t, id, "COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM airdrop_jobs WHERE airdrop_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("state machine guards: can't validate empty, can't launch a draft, can't add recipients after validation")
    void stateGuards() throws Exception {
        Tenant t = registerTenant("guards");
        UUID empty = createAirdrop(t, "Empty");

        JsonNode noRecipients = postJson("/api/v1/user/airdrops/" + empty + "/validate", t.accessToken(), null, 400);
        assertThat(noRecipients.get("code").asText()).isEqualTo("NO_RECIPIENTS");
        launch(t, empty, "k-" + UUID.randomUUID(), 409); // still DRAFT

        UUID ready = readyAirdrop(t, 1);
        postJson("/api/v1/user/airdrops/" + ready + "/recipients", t.accessToken(),
                Map.of("recipients", List.of(Map.of("recipientAddress", "0xlateaddress1", "amount", 5))), 409);
    }

    @Test
    @DisplayName("recipient input validation: bad address and non-positive amount are rejected")
    void recipientValidation() throws Exception {
        Tenant t = registerTenant("recv");
        UUID id = createAirdrop(t, "Recipients");
        String url = "/api/v1/user/airdrops/" + id + "/recipients";

        JsonNode badAddr = postJson(url, t.accessToken(),
                Map.of("recipients", List.of(Map.of("recipientAddress", "has space", "amount", 5))), 400);
        assertThat(badAddr.get("code").asText()).isEqualTo("INVALID_RECIPIENT_ADDRESS");

        postJson(url, t.accessToken(),
                Map.of("recipients", List.of(Map.of("recipientAddress", "0xvalidaddr1", "amount", 0))), 400);
        postJson(url, t.accessToken(), Map.of("recipients", List.of()), 400);
    }

    @Test
    @DisplayName("totals are recomputed as recipients are added")
    void totalsRecomputed() throws Exception {
        Tenant t = registerTenant("totals");
        UUID id = createAirdrop(t, "Totals");
        postJson("/api/v1/user/airdrops/" + id + "/recipients", t.accessToken(), Map.of("recipients", List.of(
                Map.of("recipientAddress", "0xaddress001", "amount", 10.5),
                Map.of("recipientAddress", "0xaddress002", "amount", 4.5))), 200);
        JsonNode a = getJson("/api/v1/user/airdrops/" + id, t.accessToken(), 200);
        assertThat(a.get("recipientCount").asInt()).isEqualTo(2);
        assertThat(a.get("totalAmount").decimalValue().doubleValue()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("cancel works before launch, and a cancelled airdrop can't be launched or cancelled again")
    void cancelFlow() throws Exception {
        Tenant t = registerTenant("cancel");
        UUID id = readyAirdrop(t, 1);

        JsonNode cancelled = postJson("/api/v1/user/airdrops/" + id + "/cancel", t.accessToken(), null, 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

        launch(t, id, "k-" + UUID.randomUUID(), 409);
        postJson("/api/v1/user/airdrops/" + id + "/cancel", t.accessToken(), null, 409);
    }

    @Test
    @DisplayName("a completed airdrop can no longer be cancelled")
    void cannotCancelCompleted() throws Exception {
        Tenant t = registerTenant("cancelDone");
        UUID id = readyAirdrop(t, 1);
        launch(t, id, "k-" + UUID.randomUUID(), 202);
        awaitStatus(t, id, "COMPLETED");
        JsonNode err = postJson("/api/v1/user/airdrops/" + id + "/cancel", t.accessToken(), null, 409);
        assertThat(err.get("code").asText()).isEqualTo("CANNOT_CANCEL");
    }

    @Test
    @DisplayName("dashboard and admin audit log reflect real activity")
    void dashboardAndAudit() throws Exception {
        Tenant t = registerTenant("dash");
        UUID id = readyAirdrop(t, 3);
        launch(t, id, "k-" + UUID.randomUUID(), 202);
        awaitStatus(t, id, "COMPLETED");

        JsonNode dash = getJson("/api/v1/user/dashboard", t.accessToken(), 200);
        assertThat(dash.get("totalAirdrops").asLong()).isEqualTo(1);
        assertThat(dash.get("byStatus").get("COMPLETED").asLong()).isEqualTo(1);
        assertThat(dash.get("totalRecipientsCompleted").asLong()).isEqualTo(3);

        JsonNode audit = getJson("/api/v1/admin/audit-logs?size=200", adminToken(), 200);
        assertThat(audit.get("items").toString()).contains("AIRDROP_LAUNCH");
    }
}
