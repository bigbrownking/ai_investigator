package org.di.digital.service.impl.qualification;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.cases.Case;
import org.di.digital.model.qualification.CaseQualification;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.qualification.CaseQualificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class QualificationWriter {

    private final CaseRepository caseRepository;
    private final CaseQualificationRepository caseQualificationRepository;

    @Transactional
    public void saveQualification(String caseNumber, String text) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));

        CaseQualification qualification = caseQualificationRepository
                .findByCaseEntityNumber(caseNumber)
                .orElseGet(() -> CaseQualification.builder()
                        .caseEntity(entity)
                        .build());

        qualification.setContent(text);
        qualification.setGeneratedAt(LocalDateTime.now());

        caseQualificationRepository.save(qualification);
    }
}