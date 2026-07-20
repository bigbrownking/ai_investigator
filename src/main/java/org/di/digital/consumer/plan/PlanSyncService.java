package org.di.digital.consumer.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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

    private final WebClient.Builder webClientBuilder;
    private final PlanPersistenceService persistenceService;

    @Value("${model.host}")
    private String planHost;

    @Value("${plan.port}")
    private String planPort;

    public void sync(String caseNumber) {
        boolean hasPlan;
        try {
            hasPlan = persistenceService.hasPlan(caseNumber);
        } catch (Exception e) {
            log.error("Plan sync precheck failed for case {}: {}", caseNumber, e.getMessage(), e);
            return;
        }

        if (!hasPlan) {
            log.info("No plan exists yet for case {}, skipping append sync", caseNumber);
            return;
        }

        Map<String, Object> response;
        try {
            MultiValueMap<String, Object> body = planBody(caseNumber, "append");
            response = webClientBuilder.build()
                    .post()
                    .uri(planGeneratorUrl(planHost, planPort))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Plan sync failed for case {}: {}", caseNumber, e.getMessage(), e);
            return;
        }

        if (response == null) {
            log.warn("Empty response from plan generator (append) for case {}", caseNumber);
            return;
        }

        try {
            persistenceService.savePlan(caseNumber, response);
            log.info("Plan appended/synced for case {}", caseNumber);
        } catch (Exception e) {
            log.error("Plan save failed for case {}: {}", caseNumber, e.getMessage(), e);
        }
    }
}