package org.di.digital.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.qualification.QualificationAnalyticsExternalResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseAnalytics;
import org.di.digital.repository.cases.CaseAnalyticsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

import static org.di.digital.util.requests.RequestUrlBuilder.analyticsQualification;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseAnalyticsService {

    private final CaseAnalyticsRepository analyticsRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${model.host}")
    private String host;

    @Value("${qualification.analytics.port}")
    private String port;

    @Transactional
    public void recalculateQualification(Case caseEntity) {
        try {
            QualificationAnalyticsExternalResponse response = fetchAnalytics(caseEntity.getNumber());

            CaseAnalytics analytics = analyticsRepository
                    .findByCaseEntityId(caseEntity.getId())
                    .orElseGet(() -> CaseAnalytics.builder()
                            .caseEntity(caseEntity)
                            .build());

            analytics.setQualificationScorePercent(response.getScorePercent());
            analytics.setQualificationSummary(response.getSummary());
            analytics.setComputedAt(LocalDateTime.now());

            analyticsRepository.save(analytics);
            log.info("Qualification analytics saved for case {}: score={}",
                    caseEntity.getNumber(), response.getScorePercent());

        } catch (Exception e) {
            log.error("Failed to fetch/save qualification analytics for case {}",
                    caseEntity.getNumber(), e);
        }
    }

    private QualificationAnalyticsExternalResponse fetchAnalytics(String caseNumber) {
        return webClientBuilder.build()
                .get()
                .uri(analyticsQualification(host, port, caseNumber))
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(QualificationAnalyticsExternalResponse.class)
                .block();
    }
}