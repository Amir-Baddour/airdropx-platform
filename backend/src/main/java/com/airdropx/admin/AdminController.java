package com.airdropx.admin;

import com.airdropx.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Authorization is enforced centrally in SecurityConfig (ROLE_PLATFORM_ADMIN required for all of /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardSummary> dashboard() {
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/companies")
    public ResponseEntity<PageResponse<CompanySummary>> companies(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(adminService.listCompanies(PageRequest.of(page, size))));
    }

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserSummary>> users(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(adminService.listUsers(PageRequest.of(page, size))));
    }

    @GetMapping("/airdrops")
    public ResponseEntity<PageResponse<AdminAirdropSummary>> airdrops(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(adminService.listAirdrops(PageRequest.of(page, size))));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<PageResponse<AuditLogSummary>> auditLogs(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(PageResponse.from(adminService.listAuditLogs(PageRequest.of(page, size))));
    }
}
