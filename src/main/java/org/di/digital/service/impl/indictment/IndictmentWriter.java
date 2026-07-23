package org.di.digital.service.impl.indictment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.indictment.CaseIndictment;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.indictment.CaseIndictmentRepository;
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
public class IndictmentWriter {

    private final CaseRepository caseRepository;
    private final CaseIndictmentRepository caseIndictmentRepository;
    private final ObjectMapper mapper;

    @Transactional
    public void saveIndictmentRaw(String caseNumber, String rawJson, boolean isDone) {
        if (rawJson == null || rawJson.isBlank()) return;

        CaseIndictment indictment = getOrCreate(caseNumber);

        try {
            var node = mapper.readTree(rawJson);

            if (node.has("result") && node.get("result").isArray()) {
                List<Map<String, Object>> sections = mapper.convertValue(
                        node.get("result"),
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                indictment.setSections(sections);
                indictment.setContent(null);

            } else if (node.isArray()) {
                List<Map<String, Object>> sections = mapper.convertValue(
                        node,
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                indictment.setSections(sections);
                indictment.setContent(null);

            } else if (node.has("answer")) {
                indictment.setContent(node.get("answer").asText());
                indictment.setSections(null);

            } else {
                indictment.setContent(rawJson);
                indictment.setSections(null);
            }
        } catch (Exception e) {
            log.warn("Could not parse indictment as JSON, saving as plain text for case {}", caseNumber);
            indictment.setContent(rawJson);
            indictment.setSections(null);
        }

        indictment.setGeneratedAt(LocalDateTime.now());
        indictment.setFinalDone(isDone);
        caseIndictmentRepository.save(indictment);
    }

    @Transactional
    public void saveSingleSection(String caseNumber, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;

        try {
            var node = mapper.readTree(rawJson);
            if (!node.has("result")) return;

            Map<String, Object> newSection = mapper.convertValue(node.get("result"), Map.class);
            Integer sectionId = (Integer) newSection.get("id");
            if (sectionId == null) {
                log.warn("Section without id in response for case {}", caseNumber);
                return;
            }

            CaseIndictment indictment = getOrCreate(caseNumber);

            List<Map<String, Object>> sections = indictment.getSections() != null
                    ? new ArrayList<>(indictment.getSections())
                    : new ArrayList<>();

            sections.removeIf(s -> sectionId.equals(s.get("id")));
            sections.add(newSection);
            sections.sort(Comparator.comparingInt(s -> (Integer) s.get("id")));

            indictment.setSections(sections);
            caseIndictmentRepository.save(indictment);
        } catch (Exception e) {
            log.error("Failed to parse indictment section response for case {}", caseNumber, e);
        }
    }

    private CaseIndictment getOrCreate(String caseNumber) {
        Case entity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseNumber));
        return caseIndictmentRepository.findByCaseEntityNumber(caseNumber)
                .orElseGet(() -> CaseIndictment.builder().caseEntity(entity).build());
    }
}