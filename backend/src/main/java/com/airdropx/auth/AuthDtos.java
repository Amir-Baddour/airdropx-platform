package com.airdropx.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * All request/response shapes for the auth module in one file. None of these are used outside this
 * package, so none are public — see README > "Adaptations from the diagrams" for why DTOs are grouped
 * this way instead of one file per class.
 */
record RegisterRequest(
        @NotBlank(message = "companyName is required") String companyName,
        @NotBlank @Email(message = "companyEmail must be a valid email") String companyEmail,
        @NotBlank @Email(message = "email must be a valid email") String email,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
        @NotBlank String firstName,
        @NotBlank String lastName
) {}

record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}

record RefreshRequest(
        @NotBlank String refreshToken
) {}

record UserSummary(UUID id, String email, String firstName, String lastName, String phone, String address, String role, UUID companyId) {}

record UpdateProfileRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phone,
        String address
) {}

record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, UserSummary user) {}
