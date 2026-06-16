package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.service.impl.plan.PlanActionNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanActionScheduler {

    private final CaseRepository caseRepository;
    private final PlanActionNotifier planActionNotifier;

    @Scheduled(cron = "0 0 9,15,18 * * *", zone = "Asia/Almaty")
    public void checkRedActions() {
        log.info("Scheduled red action check started");

        List<Case> activeCases = caseRepository.findByPlanStatusIn(
                List.of(PlanStatus.APPROVED_L3)
        );

        for (Case caseEntity : activeCases) {
            try {
                planActionNotifier.checkAndNotifyRedActions(caseEntity);
            } catch (Exception e) {
                log.error("Error checking red actions for case {}: {}",
                        caseEntity.getNumber(), e.getMessage());
            }
        }

        log.info("Scheduled red action check finished, processed {} cases", activeCases.size());
    }
}
