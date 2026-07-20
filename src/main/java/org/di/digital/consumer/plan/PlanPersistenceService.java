package org.di.digital.consumer.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.repository.cases.CaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPersistenceService {

    private final CaseRepository caseRepository;

    @Transactional(readOnly = true)
    public boolean hasPlan(String caseNumber) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        return caseEntity.getPlan() != null;
    }

    @Transactional
    public void savePlan(String caseNumber, Map<String, Object> plan) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);
    }
}