package com.airdropx;

import com.airdropx.common.enums.CompanyStatus;
import com.airdropx.common.enums.UserRole;
import com.airdropx.model.User;
import com.airdropx.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Boots the FULL application (real Flyway migrations, real JPA, real Redis queue, real worker thread)
 * against throwaway Postgres + Redis containers. No mocks of the database or the queue — that is the
 * point: the bugs this project actually had (job stuck QUEUED, 403-instead-of-401, recovery skipping a
 * RUNNING airdrop) all lived in the seams between components, which mocks would have hidden.
 *
 * Containers are started once per JVM and shared by every test class (singleton pattern), and the
 * Spring context is cached across classes because they all share this configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    // ---- helpers ---------------------------------------------------------------------------------

    /** A registered tenant: company owner + their access/refresh tokens. */
    public record Tenant(String email, String password, String accessToken, String refreshToken, UUID companyId) {}

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.io";
    }

    protected Tenant registerTenant(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String password = "Passw0rd!123";
        JsonNode body = postJson("/api/v1/auth/register", null, Map.of(
                "companyName", prefix + " Inc", "companyEmail", uniqueEmail("co-" + prefix),
                "email", email, "password", password, "firstName", "Test", "lastName", "Owner"), 201);
        return new Tenant(email, password, body.get("accessToken").asText(), body.get("refreshToken").asText(),
                UUID.fromString(body.get("user").get("companyId").asText()));
    }

    /** Inserts a PLATFORM_ADMIN directly (there is deliberately no public endpoint to create one) and logs in. */
    protected String adminToken() throws Exception {
        String email = uniqueEmail("admin");
        userRepository.save(User.builder()
                .email(email).passwordHash(passwordEncoder.encode("Adm1nPass!123"))
                .firstName("Plat").lastName("Admin").role(UserRole.PLATFORM_ADMIN)
                .status(CompanyStatus.ACTIVE).emailVerified(true).build());
        JsonNode body = postJson("/api/v1/auth/login", null, Map.of("email", email, "password", "Adm1nPass!123"), 200);
        return body.get("accessToken").asText();
    }

    protected JsonNode postJson(String url, String token, Object body, int expectedStatus) throws Exception {
        return send(post(url), token, body, expectedStatus);
    }

    protected JsonNode putJson(String url, String token, Object body, int expectedStatus) throws Exception {
        return send(put(url), token, body, expectedStatus);
    }

    protected JsonNode getJson(String url, String token, int expectedStatus) throws Exception {
        return send(get(url), token, null, expectedStatus);
    }

    protected JsonNode send(MockHttpServletRequestBuilder req, String token, Object body, int expectedStatus) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(req).andReturn();
        int actual = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString();
        if (actual != expectedStatus) {
            throw new AssertionError("Expected HTTP " + expectedStatus + " but got " + actual + " for "
                    + result.getRequest().getMethod() + " " + result.getRequest().getRequestURI() + " — body: " + content);
        }
        return content.isBlank() ? json.createObjectNode() : json.readTree(content);
    }

    // ---- airdrop flow shortcuts ---------------------------------------------------------------------

    protected UUID createAirdrop(Tenant t, String name) throws Exception {
        JsonNode r = postJson("/api/v1/user/airdrops", t.accessToken(),
                Map.of("name", name, "description", "test", "assetType", "TOKEN"), 201);
        return UUID.fromString(r.get("id").asText());
    }

    protected void addRecipients(Tenant t, UUID airdropId, int count) throws Exception {
        List<Map<String, Object>> recipients = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            recipients.add(Map.of("recipientAddress", "0xrecipient" + UUID.randomUUID().toString().substring(0, 8),
                    "amount", 10 + i));
        }
        postJson("/api/v1/user/airdrops/" + airdropId + "/recipients", t.accessToken(),
                Map.of("recipients", recipients), 200);
    }

    protected UUID readyAirdrop(Tenant t, int recipients) throws Exception {
        UUID id = createAirdrop(t, "Ready " + UUID.randomUUID().toString().substring(0, 6));
        addRecipients(t, id, recipients);
        postJson("/api/v1/user/airdrops/" + id + "/validate", t.accessToken(), null, 200);
        return id;
    }

    protected JsonNode launch(Tenant t, UUID airdropId, String idempotencyKey, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/v1/user/airdrops/" + airdropId + "/launch");
        if (idempotencyKey != null) req.header("Idempotency-Key", idempotencyKey);
        return send(req, t.accessToken(), null, expectedStatus);
    }

    protected String airdropStatus(Tenant t, UUID airdropId) throws Exception {
        return getJson("/api/v1/user/airdrops/" + airdropId, t.accessToken(), 200).get("status").asText();
    }
}
