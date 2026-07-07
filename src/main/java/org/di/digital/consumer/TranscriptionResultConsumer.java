package org.di.digital.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.TranscriptionResultMessage;
import org.di.digital.dto.request.interrogation.CleanTranscriptRequest;
import org.di.digital.dto.response.interrogation.CleanTranscriptResponse;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.enums.QAStatusEnum;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.service.interrogation.CaseInterrogationReformulateService;
import org.di.digital.service.impl.core.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionResultConsumer {

    private final CaseInterrogationRepository interrogationRepository;
    private final TranscriptionUpdateService transcriptionUpdateService;
    private final CaseInterrogationReformulateService caseInterrogationReformulateService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${spring.rabbitmq.inter.result.queue}")
    public void handleTranscriptionResult(TranscriptionResultMessage message) {
        log.info("Received transcription result for interrogation: {}, qa: {}, fieldName: {}, status: {}",
                message.getInterrogationId(), message.getQaId(), message.getFieldName(), message.getStatus());

        try {
            CaseInterrogation interrogation = interrogationRepository
                    .findById(message.getInterrogationId())
                    .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + message.getInterrogationId()));

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

                    if(transcribedText.startsWith("Продолжение следует.")){
                        transcribedText = null;
                    }
                    try {
                        log.info("Before clean: {}", transcribedText);
                        CleanTranscriptRequest cleanRequest = CleanTranscriptRequest.builder()
                                .text(transcribedText)
                                .language(interrogation.getLanguage().equals("русском") ? "russian" : "kazakh")
                                .build();
                        log.info("Clean request: {}", cleanRequest);
                        CleanTranscriptResponse cleaned = caseInterrogationReformulateService.cleanTranscript(cleanRequest);
                        if (cleaned != null && cleaned.getCorrectedText() != null) {
                            log.info("After clean: {}", cleaned.getCorrectedText());
                            transcribedText = cleaned.getCorrectedText();
                        }

                    } catch (Exception e) {
                        log.warn("Failed to clean transcript, using original: {}", e.getMessage());
                    }

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
            if (node.has("data")) {
                return deduplicateWords(node.get("data").asText());
            }
        } catch (Exception e) {
            log.warn("Failed to parse transcription JSON, using raw text: {}", e.getMessage());
        }
        return raw;
    }

    private String deduplicateWords(String text) {
        if (text == null || text.isBlank()) return text;

        text = deduplicatePhrases(text);

        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        String lastWord = null;

        for (String word : words) {
            String normalized = word.toLowerCase().replaceAll("[^\\p{L}]", "");
            String lastNormalized = lastWord != null
                    ? lastWord.toLowerCase().replaceAll("[^\\p{L}]", "")
                    : null;

            if (!normalized.equals(lastNormalized)) {
                if (!result.isEmpty()) result.append(" ");
                result.append(word);
            }
            lastWord = word;
        }

        return result.toString();
    }
    private String deduplicatePhrases(String text) {
        String[] words = text.split("\\s+");

        for (int phraseLen = 5; phraseLen >= 2; phraseLen--) {
            words = removeDuplicatePhrases(words, phraseLen);
        }

        return String.join(" ", words);
    }
    private String[] removeDuplicatePhrases(String[] words, int phraseLen) {
        if (words.length < phraseLen * 2) return words;

        java.util.List<String> result = new java.util.ArrayList<>(java.util.Arrays.asList(words));
        int i = 0;

        while (i <= result.size() - phraseLen * 2) {
            java.util.List<String> phrase = result.subList(i, i + phraseLen);
            java.util.List<String> next = result.subList(i + phraseLen, i + phraseLen * 2);

            String phraseNorm = phrase.stream()
                    .map(w -> w.toLowerCase().replaceAll("[^\\p{L}]", ""))
                    .collect(java.util.stream.Collectors.joining(" "));

            String nextNorm = next.stream()
                    .map(w -> w.toLowerCase().replaceAll("[^\\p{L}]", ""))
                    .collect(java.util.stream.Collectors.joining(" "));

            if (phraseNorm.equals(nextNorm)) {
                result.subList(i + phraseLen, i + phraseLen * 2).clear();
            } else {
                i++;
            }
        }

        return result.toArray(new String[0]);
    }
}