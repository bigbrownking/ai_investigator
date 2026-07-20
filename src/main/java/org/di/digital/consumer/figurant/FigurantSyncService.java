package org.di.digital.consumer.figurant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.FigurantApiResponse;
import org.di.digital.repository.cases.CaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.di.digital.util.requests.RequestUrlBuilder.figurantUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class FigurantSyncService {

    private final WebClient.Builder webClientBuilder;
    private final FigurantPersistenceService persistenceService;

    @Value("${model.host}")
    private String pythonHost;

    @Value("${figurant.port}")
    private String figurantPort;

    public void sync(String caseNumber) {
        FigurantApiResponse response;
        try {
            response = webClientBuilder.build()
                    .get()
                    .uri(figurantUrl(pythonHost, figurantPort, caseNumber))
                    .retrieve()
                    .bodyToMono(FigurantApiResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("Figurant API returned error for case {}: {}", caseNumber, e.getStatusCode());
            return;
        } catch (Exception e) {
            log.error("Figurant sync failed for case {}: {}", caseNumber, e.getMessage(), e);
            return;
        }

        if (response == null || response.getFigurants() == null || response.getFigurants().isEmpty()) {
            log.info("No figurants returned for case {}", caseNumber);
            return;
        }

        persistenceService.persistFigurants(caseNumber, response.getFigurants());
        log.info("Figurant sync completed for case {}", caseNumber);
    }
}