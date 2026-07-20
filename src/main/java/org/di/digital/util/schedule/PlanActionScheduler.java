package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.plan.CasePlan;
import org.di.digital.repository.plan.CasePlanRepository;
import org.di.digital.service.impl.plan.PlanActionNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanActionScheduler {

    private final CasePlanRepository casePlanRepository;
    private final PlanActionNotifier planActionNotifier;

    @Scheduled(cron = "${scheduler.plan.action}", zone = "Asia/Almaty")
    public void checkRedActions() {
        log.info("Scheduled red action check started");

        List<CasePlan> activePlans = casePlanRepository.findByStatusIn(
                List.of(PlanStatus.APPROVED_L3)
        );

        for (CasePlan plan : activePlans) {
            try {
                planActionNotifier.checkAndNotifyRedActions(plan);
            } catch (Exception e) {
                log.error("Error checking red actions for plan {}: {}",
                        plan.getCaseEntity().getNumber(), e.getMessage());
            }
        }

        log.info("Scheduled red action check finished, processed {} plans", activePlans.size());
    }
}