package org.di.digital.consumer.transcription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationAudioRecord;
import org.di.digital.model.interrogation.CaseInterrogationOtherAudio;
import org.di.digital.model.interrogation.CaseInterrogationQA;
import org.di.digital.model.enums.QAStatusEnum;
import org.di.digital.repository.interrogation.CaseInterrogationIOtherAudioRepository;
import org.di.digital.repository.interrogation.CaseInterrogationQARepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TranscriptionUpdateService {

    private final CaseInterrogationQARepository qaRepository;
    private final CaseInterrogationIOtherAudioRepository otherAudioRepository;
    private final CaseInterrogationRepository interrogationRepository;

    public void updateStatus(Long qaId, Long recordId, QAStatusEnum status, String transcribedText) {
        log.info("updateStatus: qaId={}, recordId={}, status={}, hasText={}",
                qaId, recordId, status, transcribedText != null);

        if (qaId == null) {
            log.warn("qaId is null, skipping QA status update");
            return;
        }

        if (qaRepository.existsById(qaId)) {
            CaseInterrogationQA qa = qaRepository.findById(qaId).get();
            qa.setStatus(status);

            if (transcribedText != null && recordId != null) {
                qa.getAudioRecords().stream()
                        .filter(r -> r.getId().equals(recordId))
                        .findFirst()
                        .ifPresentOrElse(
                                r -> { r.setTranscribedText(transcribedText); r.setStatus(status); },
                                () -> log.warn("recordId {} НЕ найден в QA {} среди {}",
                                        recordId, qaId,
                                        qa.getAudioRecords().stream()
                                                .map(CaseInterrogationAudioRecord::getId).toList())
                        );

                if (Boolean.TRUE.equals(qa.getManuallyEdited())) {
                    String existing = qa.getAnswer();
                    qa.setAnswer((existing != null && !existing.isBlank())
                            ? existing + "\n\n" + transcribedText
                            : transcribedText);
                }
                else {
                    String concatenated = qa.getAudioRecords().stream()
                            .filter(r -> r.getTranscribedText() != null)
                            .sorted(Comparator.comparing(CaseInterrogationAudioRecord::getCreatedAt))
                            .map(CaseInterrogationAudioRecord::getTranscribedText)
                            .collect(Collectors.joining("\n\n"));
                    qa.setAnswer(concatenated);
                }
            }

            qaRepository.save(qa);
            return;
        }

        CaseInterrogationOtherAudio otherAudio = otherAudioRepository.findById(qaId)
                .orElseThrow(() -> new IllegalStateException("Вопрос/ответ не найден: " + qaId));

        otherAudio.setStatus(status);

        if (transcribedText != null && recordId != null) {
            otherAudio.getAudioRecords().stream()
                    .filter(r -> r.getId().equals(recordId))
                    .findFirst()
                    .ifPresent(r -> {
                        r.setTranscribedText(transcribedText);
                        r.setStatus(status);
                    });

            if (Boolean.TRUE.equals(otherAudio.getManuallyEdited())) {
                String existing = otherAudio.getText();
                otherAudio.setText((existing != null && !existing.isBlank())
                        ? existing + "\n\n" + transcribedText
                        : transcribedText);
            } else {
                String concatenated = otherAudio.getAudioRecords().stream()
                        .filter(r -> r.getTranscribedText() != null)
                        .sorted(Comparator.comparing(CaseInterrogationAudioRecord::getCreatedAt))
                        .map(CaseInterrogationAudioRecord::getTranscribedText)
                        .collect(Collectors.joining("\n\n"));
                otherAudio.setText(concatenated);
            }
        }

        otherAudioRepository.save(otherAudio);
    }

    public void updateInterrogationField(CaseInterrogation interrogation, String fieldName, String newText) {
        switch (fieldName) {
            case "confessionText" -> {
                String existing = interrogation.getConfessionText();
                interrogation.setConfessionText(
                        (existing != null && !existing.isBlank()) ? existing + "\n\n" + newText : newText
                );
            }
            case "additionalText" -> {
                String existing = interrogation.getAdditionalText();
                interrogation.setAdditionalText(
                        (existing != null && !existing.isBlank()) ? existing + "\n\n" + newText : newText
                );
            }
            default -> log.warn("Unknown fieldName '{}', skipping", fieldName);
        }
        interrogationRepository.save(interrogation);
    }
}