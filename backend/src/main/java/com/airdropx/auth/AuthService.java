package com.airdropx.auth;

import com.airdropx.common.audit.AuditLogService;
import com.airdropx.common.enums.CompanyStatus;
import com.airdropx.common.enums.UserRole;
import com.airdropx.common.exception.ApiException;
import com.airdropx.model.Company;
import com.airdropx.model.RefreshToken;
import com.airdropx.model.User;
import com.airdropx.repository.CompanyRepository;
import com.airdropx.repository.RefreshTokenRepository;
import com.airdropx.repository.UserRepository;
import com.airdropx.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    @Transactional
    AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            // Deliberately vague message — confirming "this email exists" is a minor account-enumeration
            // leak, worth avoiding even in a mock project.
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }

        Company company = companyRepository.save(Company.builder()
                .name(req.companyName())
                .email(req.companyEmail())
                .status(CompanyStatus.ACTIVE) // no manual approval step in v1 — see README > Roadmap
                .build());

        User user = userRepository.save(User.builder()
                .company(company)
                .email(req.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(req.password()))
                .firstName(req.firstName())
                .lastName(req.lastName())
                .role(UserRole.COMPANY_OWNER)
                .status(CompanyStatus.ACTIVE)
                .emailVerified(false) // email sending isn't wired up — nothing currently flips this true
                .build());

        auditLogService.record(user, company, "USER_REGISTER", "USER", user.getId(),
                Map.of("companyId", company.getId().toString()));

        return issueTokenPair(user);
    }

    @Transactional
    AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadCredentialsException("Email or password is incorrect"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email or password is incorrect");
        }
        if (user.getStatus() != CompanyStatus.ACTIVE) {
            throw ApiException.conflict("ACCOUNT_NOT_ACTIVE", "This account is " + user.getStatus().name().toLowerCase());
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditLogService.record(user, user.getCompany(), "USER_LOGIN", "USER", user.getId(), null);

        return issueTokenPair(user);
    }

    @Transactional
    AuthResponse refresh(RefreshRequest req) {
        String hash = jwtService.hashToken(req.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid"));

        if (!existing.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh token is expired or has been revoked");
        }

        // Rotation: this token is single-use. Revoking it here means a stolen-and-replayed refresh
        // token stops working the moment the legitimate client uses it next.
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        return issueTokenPair(existing.getUser());
    }

    @Transactional
    void logout(RefreshRequest req) {
        String hash = jwtService.hashToken(req.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevokedAt(Instant.now());
            refreshTokenRepository.save(rt);
        });
        // Intentionally silent if the token isn't found — logout is idempotent from the client's point of view.
    }

    @Transactional
    UserSummary updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));

        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setPhone(req.phone());
        user.setAddress(req.address());
        userRepository.save(user);

        auditLogService.record(user, user.getCompany(), "USER_PROFILE_UPDATE", "USER", user.getId(), null);

        var companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        return new UserSummary(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getAddress(), user.getRole().name(), companyId);
    }

    private AuthResponse issueTokenPair(User user) {
        Company company = user.getCompany();
        var companyId = company != null ? company.getId() : null;

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole(), companyId);

        String rawRefreshToken = jwtService.generateOpaqueToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(Instant.now().plus(jwtService.getRefreshTokenTtlDays(), ChronoUnit.DAYS))
                .build());

        UserSummary summary = new UserSummary(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getAddress(), user.getRole().name(), companyId);

        return new AuthResponse(accessToken, rawRefreshToken, jwtService.getAccessTokenTtlMinutes() * 60, summary);
    }
}
