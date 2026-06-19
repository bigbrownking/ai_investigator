package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.interrogation.CleanTranscriptRequest;
import org.di.digital.dto.request.interrogation.ReformulateQuestionRequest;
import org.di.digital.dto.response.interrogation.CleanTranscriptResponse;
import org.di.digital.dto.response.interrogation.ReformulateQuestionResponse;
import org.di.digital.service.interrogation.CaseInterrogationReformulateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import static org.di.digital.util.requests.RequestBodyBuilder.cleanTranscriptBody;
import static org.di.digital.util.requests.RequestBodyBuilder.interrogationReformulateQuestionBody;
import static org.di.digital.util.requests.RequestUrlBuilder.interrogationCleanTranscriptUrl;
import static org.di.digital.util.requests.RequestUrlBuilder.interrogationReformulateUrl;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseInterrogationReformulateServiceImpl implements CaseInterrogationReformulateService {

    private final WebClient.Builder webClientBuilder;

    @Value("${model.host}")
    private String aiHost;

    @Value("${reformulate.port}")
    private String aiPort;

    @Override
    public ReformulateQuestionResponse reformulateQuestion(ReformulateQuestionRequest request) {
        try {
            return webClientBuilder
                    .build()
                    .post()
                    .uri(interrogationReformulateUrl(aiHost, aiPort))
                    .bodyValue(interrogationReformulateQuestionBody(request))
                    .retrieve()
                    .bodyToMono(ReformulateQuestionResponse.class)
                    .block();

        } catch (Exception e) {
            log.error("Failed to reformulate question", e);

            throw new RuntimeException(
                    "Failed to reformulate question: " + e.getMessage()
            );
        }
    }

    @Override
    public CleanTranscriptResponse cleanTranscript(CleanTranscriptRequest request) {
        try {
            return webClientBuilder
                    .build()
                    .post()
                    .uri(interrogationCleanTranscriptUrl(aiHost, aiPort))
                    .bodyValue(cleanTranscriptBody(request))
                    .retrieve()
                    .bodyToMono(CleanTranscriptResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to clean transcript", e);
            throw new RuntimeException("Failed to clean transcript: " + e.getMessage());
        }
    }
}
