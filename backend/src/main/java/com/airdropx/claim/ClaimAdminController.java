package com.airdropx.claim;

import com.airdropx.common.dto.PageResponse;
import com.airdropx.common.enums.ClaimStatus;
import com.airdropx.security.JwtAuthFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Company-side claim management. Authorization (company roles only) is enforced by SecurityConfig for /api/v1/user/**. */
@RestController
@RequestMapping("/api/v1/user/airdrops/{airdropId}")
@RequiredArgsConstructor
public class ClaimAdminController {

    private final ClaimAdminService service;

    @GetMapping("/claim-settings")
    public ResponseEntity<ClaimSettingsResponse> getSettings(@PathVariable UUID airdropId) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(service.getSettings(user.getCompany().getId(), airdropId));
    }

    @PutMapping("/claim-settings")
    public ResponseEntity<ClaimSettingsResponse> updateSettings(@PathVariable UUID airdropId, @RequestBody ClaimSettingsRequest req) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(service.updateSettings(user, user.getCompany().getId(), airdropId, req));
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskResponse> addTask(@PathVariable UUID airdropId, @Valid @RequestBody TaskRequest req) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTask(user, user.getCompany().getId(), airdropId, req));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskResponse>> listTasks(@PathVariable UUID airdropId) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(service.listTasks(user.getCompany().getId(), airdropId));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID airdropId, @PathVariable UUID taskId) {
        var user = JwtAuthFilter.currentUser();
        service.deleteTask(user, user.getCompany().getId(), airdropId, taskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/claims")
    public ResponseEntity<PageResponse<ClaimResponse>> listClaims(
            @PathVariable UUID airdropId,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var user = JwtAuthFilter.currentUser();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return ResponseEntity.ok(PageResponse.from(service.listClaims(user.getCompany().getId(), airdropId, status, pageable)));
    }

    @PostMapping("/claims/{claimId}/approve")
    public ResponseEntity<ClaimResponse> approve(@PathVariable UUID airdropId, @PathVariable UUID claimId) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(service.approve(user, user.getCompany().getId(), airdropId, claimId));
    }

    @PostMapping("/claims/{claimId}/reject")
    public ResponseEntity<ClaimResponse> reject(
            @PathVariable UUID airdropId, @PathVariable UUID claimId, @Valid @RequestBody(required = false) RejectRequest req) {
        var user = JwtAuthFilter.currentUser();
        String note = req == null ? null : req.note();
        return ResponseEntity.ok(service.reject(user, user.getCompany().getId(), airdropId, claimId, note));
    }
}
