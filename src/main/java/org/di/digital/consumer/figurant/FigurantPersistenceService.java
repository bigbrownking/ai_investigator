package org.di.digital.consumer.figurant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.FigurantApiResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.interrogation.CaseFigurant;
import org.di.digital.model.interrogation.CaseFigurantReference;
import org.di.digital.repository.cases.CaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FigurantPersistenceService {

    private final CaseRepository caseRepository;

    @Transactional
    public void persistFigurants(String caseNumber, List<FigurantApiResponse.FigurantDto> figurants) {
        Case caseEntity = caseRepository.findByNumber(caseNumber).orElse(null);
        if (caseEntity == null) {
            log.error("Дело не найдено: {}", caseNumber);
            return;
        }

        for (FigurantApiResponse.FigurantDto dto : figurants) {
            boolean exists = caseEntity.getFigurants().stream()
                    .anyMatch(f -> dto.getId().equals(f.getExternalId()));
            if (exists) {
                log.debug("Figurant {} already exists in case {}, skipping", dto.getId(), caseNumber);
                continue;
            }

            CaseFigurant figurant = CaseFigurant.builder()
                    .externalId(dto.getId())
                    .fio(dto.getName())
                    .role(dto.getType())
                    .details(dto.getDetails())
                    .caseEntity(caseEntity)
                    .build();

            if (dto.getReferences() != null) {
                List<CaseFigurantReference> refs = dto.getReferences().stream()
                        .map(r -> CaseFigurantReference.builder()
                                .referenceId(r.getReferenceId())
                                .filePath(r.getFilePath())
                                .figurant(figurant)
                                .build())
                        .toList();
                figurant.getReferences().addAll(refs);
            }

            caseEntity.addFigurant(figurant);
            log.info("Added figurant {} to case {}", dto.getName(), caseNumber);
        }

        caseRepository.save(caseEntity);
    }
}