package com.airdropx;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The recipient claim system: tasks → public claim → company review → existing distribution pipeline. */
class ClaimsIntegrationTest extends AbstractIntegrationTest {

    private record Setup(Tenant tenant, UUID airdropId, UUID followTask, UUID noteTask) {}

    /** A DRAFT airdrop with two tasks (one needs proof, one doesn't) and claims open at 25 per claimant. */
    private Setup openAirdrop() throws Exception {
        Tenant t = registerTenant("claims");
        UUID id = createAirdrop(t, "Claimable " + UUID.randomUUID().toString().substring(0, 6));
        String base = "/api/v1/user/airdrops/" + id;

        UUID follow = UUID.fromString(postJson(base + "/tasks", t.accessToken(),
                Map.of("title", "Follow us on X", "description", "Paste your profile link", "proofRequired", true), 201)
                .get("id").asText());
        UUID note = UUID.fromString(postJson(base + "/tasks", t.accessToken(),
                Map.of("title", "Join the community", "proofRequired", false), 201).get("id").asText());

        putJson(base + "/claim-settings", t.accessToken(), Map.of("claimsOpen", true, "claimAmount", 25), 200);
        return new Setup(t, id, follow, note);
    }

    private JsonNode submitClaim(Setup s, String address, String proof, int expectedStatus) throws Exception {
        return postJson("/api/v1/public/airdrops/" + s.airdropId() + "/claims", null, Map.of(
                "address", address,
                "submissions", List.of(Map.of("taskId", s.followTask().toString(), "proof", proof))), expectedStatus);
    }

    @Test
    @DisplayName("full flow: public claim → company approves → recipient → launch → worker pays them")
    void fullFlow() throws Exception {
        Setup s = openAirdrop();
        String base = "/api/v1/user/airdrops/" + s.airdropId();

        // A stranger (no login) can see the tasks and amount.
        JsonNode pub = getJson("/api/v1/public/airdrops/" + s.airdropId(), null, 200);
        assertThat(pub.get("tasks")).hasSize(2);
        assertThat(pub.get("claimAmount").decimalValue().intValue()).isEqualTo(25);
        assertThat(pub.has("companyName")).isTrue();

        JsonNode claim = submitClaim(s, "0xAlice0001", "https://x.com/alice", 201);
        assertThat(claim.get("status").asText()).isEqualTo("PENDING");

        // Company sees it, with the proof attached.
        JsonNode queue = getJson(base + "/claims?status=PENDING", s.tenant().accessToken(), 200);
        assertThat(queue.get("totalElements").asInt()).isEqualTo(1);
        JsonNode item = queue.get("items").get(0);
        assertThat(item.get("claimantAddress").asText()).isEqualTo("0xAlice0001");
        assertThat(item.get("submissions").get(0).get("proof").asText()).isEqualTo("https://x.com/alice");

        // Approve → becomes a real recipient and totals update.
        JsonNode approved = postJson(base + "/claims/" + item.get("id").asText() + "/approve", s.tenant().accessToken(), null, 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        JsonNode airdrop = getJson(base, s.tenant().accessToken(), 200);
        assertThat(airdrop.get("recipientCount").asInt()).isEqualTo(1);
        assertThat(airdrop.get("totalAmount").decimalValue().intValue()).isEqualTo(25);

        // Claimant can check their result by address (case-insensitive).
        JsonNode status = getJson("/api/v1/public/airdrops/" + s.airdropId() + "/claims/status?address=0xALICE0001", null, 200);
        assertThat(status.get("status").asText()).isEqualTo("APPROVED");

        // Validate freezes the list and closes claiming; the public page disappears.
        postJson(base + "/validate", s.tenant().accessToken(), null, 200);
        getJson("/api/v1/public/airdrops/" + s.airdropId(), null, 404);
        submitClaim(s, "0xLateComer1", "https://x.com/late", 404);

        // Launch → existing pipeline pays the approved claimant.
        launch(s.tenant(), s.airdropId(), "k-" + UUID.randomUUID(), 202);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(airdropStatus(s.tenant(), s.airdropId())).isEqualTo("COMPLETED"));
        JsonNode recipients = getJson(base + "/recipients", s.tenant().accessToken(), 200);
        assertThat(recipients.get("items").get(0).get("status").asText()).isEqualTo("COMPLETED");

        List<String> types = getJson(base + "/events", s.tenant().accessToken(), 200).get("items").findValuesAsText("eventType");
        assertThat(types).contains("CLAIM_SUBMITTED", "CLAIM_APPROVED");
    }

    @Test
    @DisplayName("one claim per address, case-insensitively")
    void duplicateClaimRejected() throws Exception {
        Setup s = openAirdrop();
        submitClaim(s, "0xBobAddr01", "https://x.com/bob", 201);
        JsonNode err = submitClaim(s, "0xBOBADDR01", "https://x.com/bob2", 409);
        assertThat(err.get("code").asText()).isEqualTo("ALREADY_CLAIMED");
    }

    @Test
    @DisplayName("claim validation: missing required proof, unknown task, bad address")
    void claimValidation() throws Exception {
        Setup s = openAirdrop();
        String url = "/api/v1/public/airdrops/" + s.airdropId() + "/claims";

        // required proof missing
        JsonNode noProof = postJson(url, null, Map.of("address", "0xCarol0001", "submissions", List.of()), 400);
        assertThat(noProof.get("code").asText()).isEqualTo("PROOF_REQUIRED");

        // blank proof for the required task
        submitClaim(s, "0xCarol0001", "   ", 400);

        // task from nowhere
        JsonNode unknown = postJson(url, null, Map.of("address", "0xCarol0001", "submissions",
                List.of(Map.of("taskId", UUID.randomUUID().toString(), "proof", "x"))), 400);
        assertThat(unknown.get("code").asText()).isEqualTo("UNKNOWN_TASK");

        // address with a space
        JsonNode badAddr = postJson(url, null, Map.of("address", "not valid", "submissions",
                List.of(Map.of("taskId", s.followTask().toString(), "proof", "p"))), 400);
        assertThat(badAddr.get("code").asText()).isEqualTo("INVALID_ADDRESS");

        // over-long proof is rejected by bean validation
        submitClaim(s, "0xCarol0001", "x".repeat(1001), 400);
    }

    @Test
    @DisplayName("rejecting keeps the claimant out of the recipient list and shows them the reason")
    void rejectFlow() throws Exception {
        Setup s = openAirdrop();
        String base = "/api/v1/user/airdrops/" + s.airdropId();
        JsonNode claim = submitClaim(s, "0xDave00001", "https://x.com/not-really", 201);
        String claimId = claim.get("claimId").asText();

        JsonNode rejected = postJson(base + "/claims/" + claimId + "/reject", s.tenant().accessToken(),
                Map.of("note", "Profile link is not yours"), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");

        assertThat(getJson(base, s.tenant().accessToken(), 200).get("recipientCount").asInt()).isZero();

        JsonNode status = getJson("/api/v1/public/airdrops/" + s.airdropId() + "/claims/status?address=0xdave00001", null, 200);
        assertThat(status.get("status").asText()).isEqualTo("REJECTED");
        assertThat(status.get("reviewNote").asText()).isEqualTo("Profile link is not yours");

        // already reviewed → can't approve or reject again
        JsonNode err = postJson(base + "/claims/" + claimId + "/approve", s.tenant().accessToken(), null, 409);
        assertThat(err.get("code").asText()).isEqualTo("CLAIM_ALREADY_REVIEWED");
        postJson(base + "/claims/" + claimId + "/reject", s.tenant().accessToken(), null, 409);
    }

    @Test
    @DisplayName("can't open claims without tasks or an amount; closed claims are invisible to the public")
    void settingsGuards() throws Exception {
        Tenant t = registerTenant("settings");
        UUID id = createAirdrop(t, "Settings");
        String base = "/api/v1/user/airdrops/" + id;

        JsonNode noAmount = putJson(base + "/claim-settings", t.accessToken(), Map.of("claimsOpen", true), 400);
        assertThat(noAmount.get("code").asText()).isEqualTo("CLAIM_AMOUNT_REQUIRED");
        JsonNode noTasks = putJson(base + "/claim-settings", t.accessToken(), Map.of("claimsOpen", true, "claimAmount", 10), 400);
        assertThat(noTasks.get("code").asText()).isEqualTo("NO_TASKS");
        putJson(base + "/claim-settings", t.accessToken(), Map.of("claimsOpen", false, "claimAmount", -5), 400);

        // never opened → public 404 (indistinguishable from "doesn't exist")
        getJson("/api/v1/public/airdrops/" + id, null, 404);
        getJson("/api/v1/public/airdrops/" + UUID.randomUUID(), null, 404);
    }

    @Test
    @DisplayName("approval is blocked once the airdrop has left DRAFT")
    void approveAfterValidateBlocked() throws Exception {
        Setup s = openAirdrop();
        String base = "/api/v1/user/airdrops/" + s.airdropId();
        String claimId = submitClaim(s, "0xErin00001", "https://x.com/erin", 201).get("claimId").asText();

        addRecipients(s.tenant(), s.airdropId(), 1); // so validate has something to freeze
        postJson(base + "/validate", s.tenant().accessToken(), null, 200);

        JsonNode err = postJson(base + "/claims/" + claimId + "/approve", s.tenant().accessToken(), null, 409);
        assertThat(err.get("code").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    @DisplayName("a task that already has submissions can't be removed")
    void taskWithSubmissionsProtected() throws Exception {
        Setup s = openAirdrop();
        submitClaim(s, "0xFrank0001", "https://x.com/frank", 201);
        JsonNode err = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/user/airdrops/" + s.airdropId() + "/tasks/" + s.followTask()),
                s.tenant().accessToken(), null, 409);
        assertThat(err.get("code").asText()).isEqualTo("TASK_HAS_SUBMISSIONS");
        // the unused task can go
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/user/airdrops/" + s.airdropId() + "/tasks/" + s.noteTask()),
                s.tenant().accessToken(), null, 204);
    }

    @Test
    @DisplayName("another company can't see, review or configure this company's claims (404)")
    void tenantIsolation() throws Exception {
        Setup s = openAirdrop();
        Tenant other = registerTenant("intruder");
        String base = "/api/v1/user/airdrops/" + s.airdropId();
        String claimId = submitClaim(s, "0xGina00001", "https://x.com/gina", 201).get("claimId").asText();

        getJson(base + "/claims", other.accessToken(), 404);
        getJson(base + "/tasks", other.accessToken(), 404);
        getJson(base + "/claim-settings", other.accessToken(), 404);
        postJson(base + "/claims/" + claimId + "/approve", other.accessToken(), null, 404);
        postJson(base + "/tasks", other.accessToken(), Map.of("title", "sneaky"), 404);
        putJson(base + "/claim-settings", other.accessToken(), Map.of("claimsOpen", false), 404);

        // and the claim is still pending for the real owner
        assertThat(getJson(base + "/claims?status=PENDING", s.tenant().accessToken(), 200).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("public submissions are rate limited per IP (429)")
    void rateLimited() throws Exception {
        Setup s = openAirdrop();
        String ip = "203.0.113." + (1 + new java.util.Random().nextInt(250));
        String url = "/api/v1/public/airdrops/" + s.airdropId() + "/claims/status?address=0xnobody0001";

        // test profile allows 5 per minute per IP
        for (int i = 0; i < 5; i++) {
            int status = mvc.perform(get(url).with(r -> { r.setRemoteAddr(ip); return r; })).andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(404); // no such claim — but not limited yet
        }
        int limited = mvc.perform(get(url).with(r -> { r.setRemoteAddr(ip); return r; })).andReturn().getResponse().getStatus();
        assertThat(limited).isEqualTo(429);

        // a different IP is unaffected
        int other = mvc.perform(get(url).with(r -> { r.setRemoteAddr("198.51.100.7"); return r; })).andReturn().getResponse().getStatus();
        assertThat(other).isEqualTo(404);
    }
}
