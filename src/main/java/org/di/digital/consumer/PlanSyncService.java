package org.di.digital.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.repository.cases.CaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.di.digital.util.requests.RequestUrlBuilder.planGeneratorUrl;
import static org.di.digital.util.requests.RequestBodyBuilder.planBody;
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanSyncService {
    private final CaseRepository caseRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${model.host}")
    private String planHost;

    @Value("${plan.port}")
    private String planPort;

    @Transactional
    public void sync(String caseNumber) {
        try {
            Case caseEntity = caseRepository.findByNumber(caseNumber)
                    .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

            if (caseEntity.getPlan() == null) {
                log.info("No plan exists yet for case {}, skipping append sync", caseNumber);
                return;
            }

            MultiValueMap<String, Object> body = planBody(caseNumber, "append");

            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(planGeneratorUrl(planHost, planPort))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                log.warn("Empty response from plan generator (append) for case {}", caseNumber);
                return;
            }

            caseEntity.setPlan(response);
            caseRepository.save(caseEntity);

            log.info("Plan appended/synced for case {}", caseNumber);

        } catch (Exception e) {
            log.error("Plan sync failed for case {}: {}", caseNumber, e.getMessage(), e);
        }
    }
}