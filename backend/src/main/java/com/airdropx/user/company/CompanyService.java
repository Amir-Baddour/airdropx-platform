package com.airdropx.user.company;

import com.airdropx.common.audit.AuditLogService;
import com.airdropx.common.exception.ApiException;
import com.airdropx.model.Company;
import com.airdropx.model.User;
import com.airdropx.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    CompanyResponse getMine(UUID companyId) {
        return toResponse(findOrThrow(companyId));
    }

    @Transactional
    CompanyResponse updateMine(User actor, UUID companyId, UpdateCompanyRequest req) {
        Company company = findOrThrow(companyId);

        company.setName(req.name());
        company.setLegalName(req.legalName());
        company.setPhone(req.phone());
        company.setWebsite(req.website());
        company.setDescription(req.description());
        company.setCountry(req.country());
        companyRepository.save(company);

        auditLogService.record(actor, company, "COMPANY_UPDATE", "COMPANY", company.getId(),
                Map.of("name", req.name()));

        return toResponse(company);
    }

    private Company findOrThrow(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> ApiException.notFound("Company"));
    }

    private CompanyResponse toResponse(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getLegalName(), c.getEmail(), c.getPhone(),
                c.getWebsite(), c.getDescription(), c.getCountry(), c.getStatus().name(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
