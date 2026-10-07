package com.airdropx.auth;

import com.airdropx.security.JwtAuthFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> me() {
        var user = JwtAuthFilter.currentUser();
        var companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        return ResponseEntity.ok(new UserSummary(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getAddress(), user.getRole().name(), companyId));
    }

    /**
     * Deliberately excludes email — changing a login identifier safely needs re-verification of the
     * new address, which needs email sending, which isn't wired up in this project (see README > Roadmap).
     * Rather than silently omit that the way the whole "personal profile" feature was originally omitted,
     * it's called out here directly: this is a known, named boundary, not another gap to discover later.
     */
    @PutMapping("/me")
    public ResponseEntity<UserSummary> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(authService.updateProfile(user.getId(), req));
    }
}
