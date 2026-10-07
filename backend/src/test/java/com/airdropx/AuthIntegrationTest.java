package com.airdropx;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("register creates company + owner and returns a usable token pair")
    void registerAndMe() throws Exception {
        Tenant t = registerTenant("reg");
        JsonNode me = getJson("/api/v1/auth/me", t.accessToken(), 200);
        assertThat(me.get("email").asText()).isEqualTo(t.email());
        assertThat(me.get("role").asText()).isEqualTo("COMPANY_OWNER");
        assertThat(me.get("companyId").asText()).isEqualTo(t.companyId().toString());
    }

    @Test
    @DisplayName("registering the same email twice is a 409, not a 500")
    void duplicateEmail() throws Exception {
        Tenant t = registerTenant("dup");
        JsonNode err = postJson("/api/v1/auth/register", null, Map.of(
                "companyName", "Other", "companyEmail", uniqueEmail("x"), "email", t.email(),
                "password", "Passw0rd!123", "firstName", "A", "lastName", "B"), 409);
        assertThat(err.get("code").asText()).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    @DisplayName("invalid registration payload returns field-level validation errors")
    void validation() throws Exception {
        JsonNode err = postJson("/api/v1/auth/register", null, Map.of(
                "companyName", "X", "companyEmail", "not-an-email", "email", "nope",
                "password", "short", "firstName", "A", "lastName", "B"), 400);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(err.get("fieldErrors").has("password")).isTrue();
        assertThat(err.get("fieldErrors").has("email")).isTrue();
    }

    @Test
    @DisplayName("wrong password and unknown email both return the same 401")
    void badLogin() throws Exception {
        Tenant t = registerTenant("login");
        JsonNode wrongPw = postJson("/api/v1/auth/login", null, Map.of("email", t.email(), "password", "wrong-password"), 401);
        JsonNode unknown = postJson("/api/v1/auth/login", null, Map.of("email", uniqueEmail("ghost"), "password", "whatever123"), 401);
        assertThat(wrongPw.get("message").asText()).isEqualTo(unknown.get("message").asText());
    }

    @Test
    @DisplayName("no token on a protected route is 401 (so the client knows to refresh), not 403")
    void unauthenticatedIs401() throws Exception {
        getJson("/api/v1/user/airdrops", null, 401);
        getJson("/api/v1/auth/me", null, 401);
        getJson("/api/v1/user/airdrops", "garbage.token.value", 401);
    }

    @Test
    @DisplayName("refresh tokens rotate: the old one stops working after use")
    void refreshRotation() throws Exception {
        Tenant t = registerTenant("rot");
        JsonNode second = postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", t.refreshToken()), 200);
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(t.refreshToken());
        // replaying the original must fail
        postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", t.refreshToken()), 401);
        // the new access token works
        getJson("/api/v1/auth/me", second.get("accessToken").asText(), 200);
    }

    @Test
    @DisplayName("logout revokes the refresh token")
    void logoutRevokes() throws Exception {
        Tenant t = registerTenant("out");
        postJson("/api/v1/auth/logout", null, Map.of("refreshToken", t.refreshToken()), 204);
        postJson("/api/v1/auth/refresh", null, Map.of("refreshToken", t.refreshToken()), 401);
    }
}
