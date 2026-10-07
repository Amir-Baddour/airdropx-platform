package com.airdropx.common.idempotency;

import com.airdropx.common.exception.ApiException;
import com.airdropx.model.IdempotencyKey;
import com.airdropx.model.User;
import com.airdropx.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wraps a mutating operation so that replaying it with the same (user, Idempotency-Key) pair returns
 * the original result instead of executing twice — this is what stops a client retry (or a double
 * click) from launching the same airdrop's distribution job two times. See idempotency_keys migration
 * and the "launch twice" edge case in the ERD.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int WINDOW_HOURS = 24;

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> ResponseEntity<T> execute(
            User user, String idempotencyKey, String endpoint, Object requestBody,
            Class<T> responseType, Supplier<ResponseEntity<T>> action
    ) {
        String requestHash = hash(requestBody);

        Optional<IdempotencyKey> existing = repository.findByUserIdAndKeyValue(user.getId(), idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used for a different request body");
            }
            // Same key, same payload: this is a genuine retry. Replay the stored result, don't re-execute.
            T body = objectMapper.convertValue(record.getResponseBody(), responseType);
            return ResponseEntity.status(record.getResponseStatus()).body(body);
        }

        ResponseEntity<T> response = action.get();

        IdempotencyKey record = IdempotencyKey.builder()
                .user(user)
                .keyValue(idempotencyKey)
                .endpoint(endpoint)
                .requestHash(requestHash)
                .responseStatus(response.getStatusCode().value())
                .responseBody(objectMapper.convertValue(response.getBody(), new TypeReference<Map<String, Object>>() {}))
                .expiresAt(Instant.now().plus(WINDOW_HOURS, ChronoUnit.HOURS))
                .build();
        repository.save(record);

        return response;
    }

    private String hash(Object requestBody) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash idempotency request body", e);
        }
    }
}
