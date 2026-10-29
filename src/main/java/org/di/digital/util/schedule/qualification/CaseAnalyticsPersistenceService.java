package org.di.digital.util.schedule.qualification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.qualification.QualificationAnalyticsExternalResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseAnalytics;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.repository.cases.CaseAnalyticsRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.util.schedule.qualification.CaseAnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseAnalyticsPersistenceService {

    private final CaseRepository caseRepository;
    private final CaseAnalyticsRepository analyticsRepository;

    @Transactional(readOnly = true)
    public CaseAnalyticsService.CaseRef loadCaseRef(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), String.valueOf(caseId))));
        return new CaseAnalyticsService.CaseRef(caseEntity.getNumber(), caseEntity.getLanguage());
    }

    @Transactional
    public void saveAnalytics(Long caseId, QualificationAnalyticsExternalResponse response) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), String.valueOf(caseId))));

        CaseAnalytics analytics = analyticsRepository
                .findByCaseEntityId(caseId)
                .orElseGet(() -> CaseAnalytics.builder()
                        .caseEntity(caseEntity)   // managed-сущность
                        .build());

        analytics.setQualificationScorePercent(response.getScorePercent());
        analytics.setQualificationSummary(response.getSummary());
        analytics.setComputedAt(LocalDateTime.now());

        analyticsRepository.save(analytics);
    }

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}