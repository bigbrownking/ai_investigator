package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.service.CaseAnalyticsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QualificationAnalyticsScheduler {

    private final CaseAnalyticsService caseAnalyticsService;
    private final CaseRepository caseRepository;

    @Scheduled(cron = "${scheduler.qualification.analytics}", zone = "Asia/Almaty")
    public void recalculateAllQualificationAnalytics() {
        log.info("Starting scheduled qualification analytics recalculation");

        List<Case> cases = caseRepository.findAllWithBothQualifications();

        log.info("Found {} cases with both qualifications", cases.size());

        int success = 0;
        int failed = 0;

        for (Case caseEntity : cases) {
            try {
                caseAnalyticsService.recalculateQualification(caseEntity);
                success++;
            } catch (Exception e) {
                log.error("Failed to recalculate analytics for case {}", caseEntity.getNumber(), e);
                failed++;
            }
        }

        log.info("Qualification analytics recalculation finished: success={}, failed={}", success, failed);
    }
}