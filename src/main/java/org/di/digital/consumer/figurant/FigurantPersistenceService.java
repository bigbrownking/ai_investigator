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

        int oldCount = caseEntity.getFigurants().size();
        caseEntity.getFigurants().clear();

        if (figurants != null) {
            for (FigurantApiResponse.FigurantDto dto : figurants) {
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
            }
        }

        caseRepository.save(caseEntity);

        log.info("Replaced figurants in case {}: {} old -> {} new",
                caseNumber, oldCount, figurants == null ? 0 : figurants.size());
    }
}