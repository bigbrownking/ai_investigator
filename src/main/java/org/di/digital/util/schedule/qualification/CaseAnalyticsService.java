package org.di.digital.util.schedule.qualification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.qualification.QualificationAnalyticsExternalResponse;
import org.di.digital.service.CaseAnalyticsPersistenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import static org.di.digital.util.requests.RequestUrlBuilder.analyticsQualification;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseAnalyticsService {

    private final WebClient.Builder webClientBuilder;
    private final CaseAnalyticsPersistenceService persistenceService;

    @Value("${model.host}")
    private String host;

    @Value("${qualification.analytics.port}")
    private String port;

    public void recalculateQualification(Long caseId) {
        CaseRef ref;
        try {
            ref = persistenceService.loadCaseRef(caseId);
        } catch (Exception e) {
            log.error("Analytics precheck failed for case id {}: {}", caseId, e.getMessage(), e);
            return;
        }

        QualificationAnalyticsExternalResponse response;
        try {
            response = fetchAnalytics(ref.number(), ref.language());
        } catch (Exception e) {
            log.error("Failed to fetch qualification analytics for case {}: {}",
                    ref.number(), e.getMessage(), e);
            return;
        }

        if (response == null) {
            log.warn("Empty analytics response for case {}", ref.number());
            return;
        }

        try {
            persistenceService.saveAnalytics(caseId, response);
            log.info("Qualification analytics saved for case {}: score={}",
                    ref.number(), response.getScorePercent());
        } catch (Exception e) {
            log.error("Failed to save qualification analytics for case {}: {}",
                    ref.number(), e.getMessage(), e);
        }
    }

    private QualificationAnalyticsExternalResponse fetchAnalytics(String caseNumber, String language) {
        return webClientBuilder.build()
                .get()
                .uri(analyticsQualification(host, port, caseNumber, language))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(QualificationAnalyticsExternalResponse.class)
                .block();
    }

    public record CaseRef(String number, String language) {}
}