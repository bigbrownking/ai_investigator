package org.di.digital.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.TranscriptionResultMessage;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.enums.QAStatusEnum;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.service.impl.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionResultConsumer {

    private final CaseInterrogationRepository interrogationRepository;
    private final TranscriptionUpdateService transcriptionUpdateService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${spring.rabbitmq.inter.result.queue}")
    public void handleTranscriptionResult(TranscriptionResultMessage message) {
        log.info("Received transcription result for interrogation: {}, qa: {}, fieldName: {}, status: {}",
                message.getInterrogationId(), message.getQaId(), message.getFieldName(), message.getStatus());

        try {
            CaseInterrogation interrogation = interrogationRepository
                    .findById(message.getInterrogationId())
                    .orElseThrow(() -> new RuntimeException("Допрос не найден: " + message.getInterrogationId()));

            boolean isOtherAudio = message.getFieldName() != null;

            switch (message.getStatus()) {
                case PROCESSING -> {
                    transcriptionUpdateService.updateStatus(
                            message.getQaId(), message.getRecordId(), QAStatusEnum.TRANSCRIBING, null);
                    if (isOtherAudio) {
                        notificationService.notifyOtherInterrogationProcessing(
                                message.getCaseNumber(), interrogation, message.getQaId(), message.getFieldName());
                    } else {
                        notificationService.notifyInterrogationProcessing(
                                message.getCaseNumber(), interrogation, message.getQaId());
                    }
                }
                case COMPLETED -> {
                    String transcribedText = extractText(message.getTranscribedText());
                    transcriptionUpdateService.updateStatus(
                            message.getQaId(), message.getRecordId(), QAStatusEnum.TRANSCRIBED, transcribedText);
                    if (isOtherAudio) {
                        transcriptionUpdateService.updateInterrogationField(
                                interrogation, message.getFieldName(), transcribedText);
                        notificationService.notifyOtherInterrogationCompleted(
                                message.getCaseNumber(), interrogation,
                                message.getQaId(), transcribedText, message.getFieldName());
                    } else {
                        notificationService.notifyInterrogationCompleted(
                                message.getCaseNumber(), interrogation, message.getQaId(), transcribedText);
                    }
                }
                case FAILED -> {
                    transcriptionUpdateService.updateStatus(
                            message.getQaId(), message.getRecordId(), QAStatusEnum.PENDING, null);
                    if (isOtherAudio) {
                        notificationService.notifyOtherInterrogationFailed(
                                message.getCaseNumber(), interrogation,
                                message.getQaId(), message.getErrorMessage(), message.getFieldName());
                    } else {
                        notificationService.notifyInterrogationFailed(
                                message.getCaseNumber(), interrogation,
                                message.getQaId(), message.getErrorMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling transcription result: {}", e.getMessage(), e);
        }
    }

    private String extractText(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.has("data")) return node.get("data").asText();
        } catch (Exception e) {
            log.warn("Failed to parse transcription JSON, using raw text: {}", e.getMessage());
        }
        return raw;
    }
}