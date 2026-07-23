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
import java.util.ArrayList;
import java.util.Comparator;
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
    @Transactional
    public void saveSingleSection(String caseNumber, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;

        try {
            var node = mapper.readTree(rawJson);

            var sectionNode = node.has("result") ? node.get("result") : node;

            if (sectionNode == null || !sectionNode.isObject() || !sectionNode.has("id")) {
                log.warn("Unexpected section response for case {}: {}", caseNumber, rawJson);
                return;
            }

            Map<String, Object> newSection = mapper.convertValue(sectionNode, Map.class);
            Integer sectionId = (Integer) newSection.get("id");

            CaseQualification qualification = getOrCreate(caseNumber);

            List<Map<String, Object>> sections = qualification.getSections() != null
                    ? new ArrayList<>(qualification.getSections())
                    : new ArrayList<>();

            sections.removeIf(s -> sectionId.equals(s.get("id")));
            sections.add(newSection);
            sections.sort(Comparator.comparingInt(s -> (Integer) s.get("id")));

            qualification.setSections(sections);
            caseQualificationRepository.save(qualification);
        } catch (Exception e) {
            log.error("Failed to parse qualification section response for case {}", caseNumber, e);
        }
    }

    private CaseQualification getOrCreate(String caseNumber) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        return caseQualificationRepository.findByCaseEntityNumber(caseNumber)
                .orElseGet(() -> CaseQualification.builder().caseEntity(entity).build());
    }
}