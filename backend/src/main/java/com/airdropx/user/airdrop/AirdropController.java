package com.airdropx.user.airdrop;

import com.airdropx.common.dto.PageResponse;
import com.airdropx.common.idempotency.IdempotencyService;
import com.airdropx.security.JwtAuthFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user/airdrops")
@RequiredArgsConstructor
public class AirdropController {

    private final AirdropService airdropService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<AirdropResponse> create(@Valid @RequestBody CreateAirdropRequest req) {
        var user = JwtAuthFilter.currentUser();
        var body = airdropService.create(user, user.getCompany().getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public ResponseEntity<PageResponse<AirdropResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var user = JwtAuthFilter.currentUser();
        var result = airdropService.list(user.getCompany().getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AirdropResponse> get(@PathVariable UUID id) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(airdropService.get(user.getCompany().getId(), id));
    }

    @PostMapping("/{id}/recipients")
    public ResponseEntity<AirdropResponse> addRecipients(@PathVariable UUID id, @Valid @RequestBody AddRecipientsRequest req) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(airdropService.addRecipients(user, user.getCompany().getId(), id, req));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<PageResponse<RecipientResponse>> listRecipients(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        var user = JwtAuthFilter.currentUser();
        var result = airdropService.listRecipients(user.getCompany().getId(), id, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<AirdropResponse> validate(@PathVariable UUID id) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(airdropService.validate(user, user.getCompany().getId(), id));
    }

    /**
     * Idempotency-Key is REQUIRED here, not optional — this is the one endpoint in the system where a
     * duplicate request has a real consequence (double-processing every recipient). See IdempotencyService.
     */
    @PostMapping("/{id}/launch")
    public ResponseEntity<LaunchResponse> launch(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var user = JwtAuthFilter.currentUser();
        return idempotencyService.execute(
                user, idempotencyKey, "POST /airdrops/" + id + "/launch", Map.of("airdropId", id.toString()),
                LaunchResponse.class,
                () -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(airdropService.launch(user, user.getCompany().getId(), id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AirdropResponse> cancel(@PathVariable UUID id) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(airdropService.cancel(user, user.getCompany().getId(), id));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<PageResponse<AirdropEventResponse>> listEvents(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        var user = JwtAuthFilter.currentUser();
        var result = airdropService.listEvents(user.getCompany().getId(), id, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }
}
