package com.airdropx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Multi-tenancy and role boundaries — the security-critical behaviour of the whole system. */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("company B cannot read, modify, launch or list company A's airdrop (404, not 403 — no existence leak)")
    void crossTenantAccessIsBlocked() throws Exception {
        Tenant a = registerTenant("tenA");
        Tenant b = registerTenant("tenB");
        UUID aAirdrop = readyAirdrop(a, 2);
        String base = "/api/v1/user/airdrops/" + aAirdrop;

        getJson(base, b.accessToken(), 404);
        getJson(base + "/recipients", b.accessToken(), 404);
        getJson(base + "/events", b.accessToken(), 404);
        postJson(base + "/recipients", b.accessToken(),
                Map.of("recipients", List.of(Map.of("recipientAddress", "0xintruder00", "amount", 1))), 404);
        postJson(base + "/validate", b.accessToken(), null, 404);
        postJson(base + "/cancel", b.accessToken(), null, 404);
        launch(b, aAirdrop, "k-" + UUID.randomUUID(), 404);

        // B's list is empty and A's airdrop is still untouched
        assertThat(getJson("/api/v1/user/airdrops", b.accessToken(), 200).get("totalElements").asLong()).isZero();
        assertThat(airdropStatus(a, aAirdrop)).isEqualTo("READY");
    }

    @Test
    @DisplayName("company users cannot reach admin endpoints")
    void companyUserCannotUseAdminApi() throws Exception {
        Tenant t = registerTenant("noadmin");
        getJson("/api/v1/admin/dashboard", t.accessToken(), 403);
        getJson("/api/v1/admin/companies", t.accessToken(), 403);
        getJson("/api/v1/admin/audit-logs", t.accessToken(), 403);
    }

    @Test
    @DisplayName("platform admin can use admin endpoints but not the company-scoped user API")
    void adminBoundaries() throws Exception {
        registerTenant("seen-by-admin");
        String admin = adminToken();

        assertThat(getJson("/api/v1/admin/companies", admin, 200).get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        getJson("/api/v1/admin/dashboard", admin, 200);
        getJson("/api/v1/admin/users", admin, 200);
        getJson("/api/v1/admin/airdrops", admin, 200);
        getJson("/api/v1/admin/audit-logs", admin, 200);

        getJson("/api/v1/user/airdrops", admin, 403);
    }
}
