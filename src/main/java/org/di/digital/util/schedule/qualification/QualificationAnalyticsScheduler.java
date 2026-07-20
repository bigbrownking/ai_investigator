package org.di.digital.util.schedule.qualification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.repository.qualification.CaseQualificationRepository;
import org.di.digital.util.schedule.qualification.CaseAnalyticsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QualificationAnalyticsScheduler {

    private final CaseAnalyticsService caseAnalyticsService;
    private final CaseQualificationRepository caseQualificationRepository;

    @Scheduled(cron = "${scheduler.qualification.analytics}", zone = "Asia/Almaty")
    public void recalculateAllQualificationAnalytics() {
        log.info("Starting scheduled qualification analytics recalculation");

        List<Long> caseIds = caseQualificationRepository.findAllCaseIdsWithBothQualifications();
        log.info("Found {} cases with both qualifications", caseIds.size());

        int success = 0;
        int failed = 0;

        for (Long caseId : caseIds) {
            try {
                caseAnalyticsService.recalculateQualification(caseId);
                success++;
            } catch (Exception e) {
                log.error("Failed to recalculate analytics for case id {}", caseId, e);
                failed++;
            }
        }

        log.info("Qualification analytics recalculation finished: success={}, failed={}", success, failed);
    }
}