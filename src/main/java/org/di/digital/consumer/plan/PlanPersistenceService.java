package org.di.digital.consumer.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.repository.cases.CaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPersistenceService {

    private final CaseRepository caseRepository;

    @Transactional(readOnly = true)
    public boolean hasPlan(String caseNumber) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        return caseEntity.getPlan() != null;
    }

    @Transactional
    public void savePlan(String caseNumber, Map<String, Object> plan) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseNumber)));
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);
    }

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}