package org.di.digital.service.impl.qualification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.qualification.CaseQualification;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.qualification.CaseQualificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationWriter {

    private final CaseRepository caseRepository;
    private final CaseQualificationRepository caseQualificationRepository;
    private final ObjectMapper mapper;

    @Transactional
    public void saveQualificationRaw(String caseNumber, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;

        CaseQualification qualification = getOrCreate(caseNumber);

        try {
            var node = mapper.readTree(rawJson);

            if (node.has("result") && node.get("result").isArray()) {
                List<Map<String, Object>> sections = mapper.convertValue(
                        node.get("result"),
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                qualification.setSections(sections);
                qualification.setContent(null);

            } else if (node.isArray()) {
                List<Map<String, Object>> sections = mapper.convertValue(
                        node,
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                qualification.setSections(sections);
                qualification.setContent(null);

            } else if (node.has("answer")) {
                qualification.setContent(node.get("answer").asText());
                qualification.setSections(null);

            } else {
                qualification.setContent(rawJson);
                qualification.setSections(null);
            }
        } catch (Exception e) {
            log.warn("Could not parse qualification as JSON, saving as plain text for case {}", caseNumber);
            qualification.setContent(rawJson);
            qualification.setSections(null);
        }

        qualification.setGeneratedAt(LocalDateTime.now());
        caseQualificationRepository.save(qualification);
    }

    private CaseQualification getOrCreate(String caseNumber) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        return caseQualificationRepository.findByCaseEntityNumber(caseNumber)
                .orElseGet(() -> CaseQualification.builder().caseEntity(entity).build());
    }
}