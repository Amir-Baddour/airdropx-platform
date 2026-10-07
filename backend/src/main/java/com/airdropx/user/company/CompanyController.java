package com.airdropx.user.company;

import com.airdropx.security.JwtAuthFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<CompanyResponse> getMine() {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(companyService.getMine(user.getCompany().getId()));
    }

    @PutMapping
    public ResponseEntity<CompanyResponse> updateMine(@Valid @RequestBody UpdateCompanyRequest req) {
        var user = JwtAuthFilter.currentUser();
        return ResponseEntity.ok(companyService.updateMine(user, user.getCompany().getId(), req));
    }
}
