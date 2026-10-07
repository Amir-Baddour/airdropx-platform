package com.airdropx.user.dashboard;

import com.airdropx.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardSummary> get() {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(dashboardService.summary(user.getCompany().getId()));
    }
}
