package com.airdropx;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the personal-profile and company-profile features, including the V11 contact-info migration. */
class ProfileIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("user can update name, phone and address, and /me reflects it")
    void updatePersonalProfile() throws Exception {
        Tenant t = registerTenant("prof");
        JsonNode updated = putJson("/api/v1/auth/me", t.accessToken(), Map.of(
                "firstName", "Majd", "lastName", "Baddour", "phone", "+96170123456", "address", "Beirut, Lebanon"), 200);
        assertThat(updated.get("phone").asText()).isEqualTo("+96170123456");

        JsonNode me = getJson("/api/v1/auth/me", t.accessToken(), 200);
        assertThat(me.get("firstName").asText()).isEqualTo("Majd");
        assertThat(me.get("address").asText()).isEqualTo("Beirut, Lebanon");
        // email is intentionally not editable
        assertThat(me.get("email").asText()).isEqualTo(t.email());
    }

    @Test
    @DisplayName("profile update with blank name is rejected")
    void blankNameRejected() throws Exception {
        Tenant t = registerTenant("blank");
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", " ");
        body.put("lastName", "X");
        JsonNode err = putJson("/api/v1/auth/me", t.accessToken(), body, 400);
        assertThat(err.get("fieldErrors").has("firstName")).isTrue();
    }

    @Test
    @DisplayName("owner can edit company profile; changes persist")
    void updateCompany() throws Exception {
        Tenant t = registerTenant("comp");
        putJson("/api/v1/user/company", t.accessToken(), Map.of(
                "name", "Renamed Co", "legalName", "Renamed Co LLC", "phone", "+9611234567",
                "website", "https://renamed.example", "description", "We airdrop things", "country", "LB"), 200);

        JsonNode company = getJson("/api/v1/user/company", t.accessToken(), 200);
        assertThat(company.get("name").asText()).isEqualTo("Renamed Co");
        assertThat(company.get("country").asText()).isEqualTo("LB");
        assertThat(company.get("website").asText()).isEqualTo("https://renamed.example");
    }
}
