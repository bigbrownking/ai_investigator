package org.di.digital.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.config.RabbitMQConfig;
import org.di.digital.dto.message.TranscriptionResultMessage;
import org.di.digital.model.CaseInterrogation;
import org.di.digital.model.CaseInterrogationProtocol;
import org.di.digital.model.CaseInterrogationQA;
import org.di.digital.model.enums.CaseInterrogationStatusEnum;
import org.di.digital.model.enums.QAStatusEnum;
import org.di.digital.repository.CaseInterrogationQARepository;
import org.di.digital.repository.CaseInterrogationRepository;
import org.di.digital.service.impl.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionResultConsumer {

    private final CaseInterrogationRepository interrogationRepository;
    private final CaseInterrogationQARepository qaRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.INTERROGATION_RESULT_QUEUE)
    public void handleTranscriptionResult(TranscriptionResultMessage message) {
        log.info("Received transcription result for interrogation: {}, qa: {}, status: {}",
                message.getInterrogationId(), message.getQaId(), message.getStatus());

        try {
            CaseInterrogation interrogation = interrogationRepository
                    .findById(message.getInterrogationId())
                    .orElseThrow(() -> new RuntimeException("Interrogation not found: " + message.getInterrogationId()));

            switch (message.getStatus()) {
                case PROCESSING -> {
                    updateQAStatus(message.getQaId(), QAStatusEnum.TRANSCRIBING, null);

                    notificationService.notifyInterrogationProcessing(
                            message.getCaseNumber(), interrogation, message.getQaId()
                    );
                }
                case COMPLETED -> {
                    String transcribedText = extractText(message.getTranscribedText());
                    updateQAStatus(message.getQaId(), QAStatusEnum.TRANSCRIBED, transcribedText);
                    notificationService.notifyInterrogationCompleted(
                            message.getCaseNumber(), interrogation, message.getQaId(), transcribedText
                    );
                }
                case FAILED -> {
                    updateQAStatus(message.getQaId(), QAStatusEnum.PENDING, null); // откатываем статус

                    notificationService.notifyInterrogationFailed(
                            message.getCaseNumber(), interrogation, message.getQaId(), message.getErrorMessage()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error handling transcription result: {}", e.getMessage(), e);
        }
    }
    private String extractText(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.has("data")) {
                return node.get("data").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse transcription JSON, using raw text: {}", e.getMessage());
        }
        return raw; // fallback — вернуть как есть
    }
    private void updateQAStatus(Long qaId, QAStatusEnum status, String transcribedText) {
        if (qaId == null) {
            log.warn("qaId is null, skipping QA status update");
            return;
        }

        CaseInterrogationQA qa = qaRepository.findById(qaId)
                .orElseThrow(() -> new RuntimeException("QA not found: " + qaId));

        qa.setStatus(status);
        if (transcribedText != null) {
            qa.setAnswer(transcribedText);
        }
        qaRepository.save(qa);

        log.info("QA {} updated: status={}, hasText={}", qaId, status, transcribedText != null);
    }
}
