package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.cases.RejectionEnrichable;
import org.di.digital.model.cases.RejectionReasonStatus;
import org.di.digital.model.enums.cases.CaseRejectionReason;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.repository.cases.RejectionReasonStatusRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CaseRejectionEnricher {
    private final RejectionReasonStatusRepository rejectionReasonStatusRepository;

    public <T extends RejectionEnrichable> void enrich(List<T> items, UserSettingsLanguage lang) {
        if (items == null || items.isEmpty()) return;

        List<Long> caseIds = items.stream().map(RejectionEnrichable::getId).toList();

        Map<Long, RejectionReasonStatus> latestByCase =
                rejectionReasonStatusRepository.findAllByCaseIdInOrderByTimestampDesc(caseIds).stream()
                        .collect(Collectors.toMap(
                                RejectionReasonStatus::getCaseId, r -> r, (a, b) -> a));

        items.forEach(p -> {
            RejectionReasonStatus r = latestByCase.get(p.getId());
            if (r != null) {
                CaseRejectionReason reason = r.getRejectionReason();
                p.setRejectionReason(reason != null ? reason.localized(lang) : null);
                p.setRejectionByFio(r.getPerformedByFio());
                p.setRejectionAt(r.getTimestamp());
            }
            if (p.getOwnerFio() != null) {
                p.setOwnerFio(p.getOwnerFio().trim().replaceAll("\\s+", " "));
            }
        });
    }
}
