package org.di.digital.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.config.RabbitMQConfig;
import org.di.digital.dto.message.TranscriptionResultMessage;
import org.di.digital.model.CaseInterrogation;
import org.di.digital.model.CaseInterrogationOtherAudio;
import org.di.digital.model.CaseInterrogationQA;
import org.di.digital.model.enums.QAStatusEnum;
import org.di.digital.repository.CaseInterrogationIOtherAudioRepository;
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
    private final CaseInterrogationIOtherAudioRepository otherAudioRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${spring.rabbitmq.inter.result.queue}")
    public void handleTranscriptionResult(TranscriptionResultMessage message) {
        log.info("Received transcription result for interrogation: {}, qa: {}, fieldName: {}, status: {}",
                message.getInterrogationId(), message.getQaId(), message.getFieldName(), message.getStatus());

        try {
            CaseInterrogation interrogation = interrogationRepository
                    .findById(message.getInterrogationId())
                    .orElseThrow(() -> new RuntimeException("Interrogation not found: " + message.getInterrogationId()));

            boolean isOtherAudio = message.getFieldName() != null;

            switch (message.getStatus()) {
                case PROCESSING -> {
                    updateStatus(message.getQaId(), QAStatusEnum.TRANSCRIBING, null);

                    if (isOtherAudio) {
                        notificationService.notifyOtherInterrogationProcessing(
                                message.getCaseNumber(), interrogation, message.getQaId(), message.getFieldName()
                        );
                    } else {
                        notificationService.notifyInterrogationProcessing(
                                message.getCaseNumber(), interrogation, message.getQaId()
                        );
                    }
                }
                case COMPLETED -> {
                    String transcribedText = extractText(message.getTranscribedText());
                    updateStatus(message.getQaId(), QAStatusEnum.TRANSCRIBED, transcribedText);

                    if (isOtherAudio) {
                        updateInterrogationField(interrogation, message.getFieldName(), transcribedText);
                        notificationService.notifyOtherInterrogationCompleted(
                                message.getCaseNumber(), interrogation,
                                message.getQaId(), transcribedText, message.getFieldName()
                        );
                    } else {
                        notificationService.notifyInterrogationCompleted(
                                message.getCaseNumber(), interrogation, message.getQaId(), transcribedText
                        );
                    }
                }
                case FAILED -> {
                    updateStatus(message.getQaId(), QAStatusEnum.PENDING, null);

                    if (isOtherAudio) {
                        notificationService.notifyOtherInterrogationFailed(
                                message.getCaseNumber(), interrogation,
                                message.getQaId(), message.getErrorMessage(), message.getFieldName()
                        );
                    } else {
                        notificationService.notifyInterrogationFailed(
                                message.getCaseNumber(), interrogation,
                                message.getQaId(), message.getErrorMessage()
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling transcription result: {}", e.getMessage(), e);
        }
    }

    private void updateInterrogationField(CaseInterrogation interrogation, String fieldName, String text) {
        switch (fieldName) {
            case "confessionText" -> interrogation.setConfessionText(text);
            case "additionalText" -> interrogation.setAdditionalText(text);
            default -> log.warn("Unknown fieldName '{}', skipping interrogation field update", fieldName);
        }
        interrogationRepository.save(interrogation);
        log.info("Interrogation {} field '{}' updated", interrogation.getId(), fieldName);
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
        return raw;
    }

    private void updateStatus(Long qaId, QAStatusEnum status, String transcribedText) {
        if (qaId == null) {
            log.warn("qaId is null, skipping QA status update");
            return;
        }

        if (qaRepository.existsById(qaId)) {
            CaseInterrogationQA qa = qaRepository.findById(qaId).get();
            qa.setStatus(status);
            if (transcribedText != null) {
                qa.setAnswer(transcribedText);
            }
            qaRepository.save(qa);
            log.info("QA {} updated: status={}, hasText={}", qaId, status, transcribedText != null);
            return;
        }

        CaseInterrogationOtherAudio otherAudio = otherAudioRepository.findById(qaId)
                .orElseThrow(() -> new RuntimeException("Neither QA nor OtherAudio found for id: " + qaId));

        otherAudio.setStatus(status);
        if (transcribedText != null) {
            otherAudio.setText(transcribedText);
        }
        otherAudioRepository.save(otherAudio);
        log.info("OtherAudio {} updated: status={}, hasText={}", qaId, status, transcribedText != null);
    }
}