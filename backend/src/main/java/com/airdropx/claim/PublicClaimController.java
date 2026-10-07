package com.airdropx.claim;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Unauthenticated endpoints a recipient uses from the shared claim link. See SecurityConfig PUBLIC_PATHS. */
@RestController
@RequestMapping("/api/v1/public/airdrops/{airdropId}")
@RequiredArgsConstructor
public class PublicClaimController {

    private final PublicClaimService service;
    private final ClaimRateLimiter rateLimiter;

    @GetMapping
    public ResponseEntity<PublicAirdropResponse> get(@PathVariable UUID airdropId) {
        return ResponseEntity.ok(service.getOpenAirdrop(airdropId));
    }

    @PostMapping("/claims")
    public ResponseEntity<ClaimStatusResponse> submit(
            @PathVariable UUID airdropId, @Valid @RequestBody SubmitClaimRequest req, HttpServletRequest http) {
        rateLimiter.check(http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(airdropId, req));
    }

    @GetMapping("/claims/status")
    public ResponseEntity<ClaimStatusResponse> status(
            @PathVariable UUID airdropId, @RequestParam String address, HttpServletRequest http) {
        rateLimiter.check(http.getRemoteAddr());
        return ResponseEntity.ok(service.status(airdropId, address));
    }
}
