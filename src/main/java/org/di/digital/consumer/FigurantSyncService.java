package org.di.digital.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.FigurantApiResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.interrogation.CaseFigurant;
import org.di.digital.model.interrogation.CaseFigurantReference;
import org.di.digital.repository.cases.CaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

import static org.di.digital.util.requests.RequestUrlBuilder.figurantUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class FigurantSyncService {

    private final CaseRepository caseRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${model.host}")
    private String pythonHost;

    @Value("${figurant.port}")
    private String figurantPort;

    @Transactional
    public void sync(String caseNumber) {
        Case caseEntity = caseRepository.findByNumber(caseNumber).orElse(null);
        if (caseEntity == null) {
            log.error("Дело не найдено: {}", caseNumber);
            return;
        }

        FigurantApiResponse response;
        try {
            response = webClientBuilder.build()
                    .get()
                    .uri(figurantUrl(pythonHost, figurantPort, caseNumber))
                    .retrieve()
                    .bodyToMono(FigurantApiResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            return;
        } catch (Exception e) {
            log.error("Figurant sync failed for case {}: {}", caseNumber, e.getMessage(), e);
            return;
        }

        if (response == null || response.getFigurants() == null || response.getFigurants().isEmpty()) {
            log.info("No figurants returned for case {}", caseNumber);
            return;
        }

        for (FigurantApiResponse.FigurantDto dto : response.getFigurants()) {
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
        log.info("Figurant sync completed for case {}", caseNumber);
    }
}