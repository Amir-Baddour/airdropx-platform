package com.airdropx.user.dashboard;

import com.airdropx.common.enums.AirdropStatus;
import com.airdropx.common.enums.RecipientStatus;
import com.airdropx.model.Airdrop;
import com.airdropx.repository.AirdropRecipientRepository;
import com.airdropx.repository.AirdropRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record RecentAirdrop(UUID id, String name, String status, Instant updatedAt) {}

record DashboardSummary(
        long totalAirdrops,
        Map<String, Long> byStatus,
        long totalRecipientsCompleted,
        long totalRecipientsFailed,
        List<RecentAirdrop> recentAirdrops
) {}

@Service
@RequiredArgsConstructor
class DashboardService {

    private final AirdropRepository airdropRepository;
    private final AirdropRecipientRepository recipientRepository;

    DashboardSummary summary(UUID companyId) {
        long total = airdropRepository.countByCompanyId(companyId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (AirdropStatus status : AirdropStatus.values()) {
            long count = airdropRepository.countByCompanyIdAndStatus(companyId, status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }

        var recent = airdropRepository
                .findByCompanyId(companyId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(this::toRecent)
                .getContent();

        long completed = recipientRepository.countByAirdrop_Company_IdAndStatus(companyId, RecipientStatus.COMPLETED);
        long failed = recipientRepository.countByAirdrop_Company_IdAndStatus(companyId, RecipientStatus.FAILED);

        return new DashboardSummary(total, byStatus, completed, failed, recent);
    }

    private RecentAirdrop toRecent(Airdrop a) {
        return new RecentAirdrop(a.getId(), a.getName(), a.getStatus().name(), a.getUpdatedAt());
    }
}
