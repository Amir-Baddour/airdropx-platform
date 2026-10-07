package com.airdropx.user.company;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

record UpdateCompanyRequest(
        @NotBlank String name,
        String legalName,
        String phone,
        String website,
        String description,
        String country
) {}

record CompanyResponse(
        UUID id, String name, String legalName, String email, String phone,
        String website, String description, String country, String status,
        Instant createdAt, Instant updatedAt
) {}
