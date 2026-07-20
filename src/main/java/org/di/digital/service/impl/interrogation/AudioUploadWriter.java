package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.AudioProcessingMessage;
import org.di.digital.dto.response.interrogation.OtherAudioResponse;
import org.di.digital.dto.response.interrogation.QAResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.enums.QAStatusEnum;
import org.di.digital.model.interrogation.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationAudioRecordRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.impl.queue.AudioQueueService;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioUploadWriter {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseInterrogationAudioRecordRepository audioRecordRepository;
    private final AudioQueueService audioQueueService;
    private final LogService logService;
    private final Mapper mapper;

    // ---- Фаза 1: валидация + timeGuard + получить fio/caseNumber для MinIO-пути ----
    @Transactional(readOnly = true)
    public AudioUploadContext validateForQaUpload(Long caseId, Long interrogationId, Long qaId,
                                                  String email, InterrogationTimeGuard timeGuard) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        timeGuard.assertCanRecord(interrogation, LocalDateTime.now());

        // проверяем существование QA заранее, чтобы не грузить файл впустую
        interrogation.getQaList().stream()
                .filter(q -> q.getId().equals(qaId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("QA не найден: " + qaId));

        return new AudioUploadContext(caseEntity.getNumber(), interrogation.getFio(),
                interrogation.getLanguage());
    }

    @Transactional(readOnly = true)
    public AudioUploadContext validateForOtherUpload(Long caseId, Long interrogationId, Long otherAudioId,
                                                     String email, InterrogationTimeGuard timeGuard) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        timeGuard.assertCanRecord(interrogation, LocalDateTime.now());

        if (otherAudioId != null) {
            interrogation.getOtherAudios().stream()
                    .filter(o -> o.getId().equals(otherAudioId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Аудио не найдено: " + otherAudioId));
        }

        return new AudioUploadContext(caseEntity.getNumber(), interrogation.getFio(),
                interrogation.getLanguage());
    }

    // ---- Фаза 3 (QA): сохранить record, отправить в очередь, вернуть DTO ----
    @Transactional
    public QAResponse persistQaAudio(Long interrogationId, Long qaId, String audioUrl,
                                     String originalFileName, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        CaseInterrogationQA qa = interrogation.getQaList().stream()
                .filter(q -> q.getId().equals(qaId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("QA не найден: " + qaId));

        qa.setStatus(QAStatusEnum.TRANSCRIBING);

        CaseInterrogationAudioRecord record = CaseInterrogationAudioRecord.builder()
                .audioFileUrl(audioUrl)
                .transcribedText(null)
                .status(QAStatusEnum.TRANSCRIBING)
                .createdAt(LocalDateTime.now())
                .qa(qa)
                .build();
        qa.getAudioRecords().add(record);

        CaseInterrogationAudioRecord savedRecord = audioRecordRepository.saveAndFlush(record);
        Long recordId = savedRecord.getId();
        String caseNumber = interrogation.getCaseEntity().getNumber();

        audioQueueService.sendAudioForProcessing(AudioProcessingMessage.builder()
                .interrogationId(interrogationId)
                .qaId(qa.getId())
                .recordId(recordId)
                .caseNumber(caseNumber)
                .audioFileUrl(audioUrl)
                .originalFileName(originalFileName)
                .language(interrogation.getLanguage())
                .email(email)
                .fieldName(null)
                .build());

        logService.log(
                String.format("Uploading qa audio %s by %s user in case %s", audioUrl, email, caseNumber),
                LogLevel.INFO, LogAction.AUDIO_UPLOADED, caseNumber, email);

        return mapper.mapToQAResponse(qa);
    }

    // ---- Фаза 3 (Other): сохранить record, отправить в очередь, вернуть DTO ----
    @Transactional
    public OtherAudioResponse persistOtherAudio(Long interrogationId, Long otherAudioId,
                                                String fieldName, String audioUrl,
                                                String originalFileName, String language, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        CaseInterrogationOtherAudio otherAudio;
        if (otherAudioId != null) {
            otherAudio = interrogation.getOtherAudios().stream()
                    .filter(o -> o.getId().equals(otherAudioId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Аудио не найдено: " + otherAudioId));
            otherAudio.setStatus(QAStatusEnum.TRANSCRIBING);
        } else {
            int orderIndex = interrogation.getOtherAudios().size();
            otherAudio = CaseInterrogationOtherAudio.builder()
                    .text(null)
                    .fieldName(fieldName)
                    .status(QAStatusEnum.TRANSCRIBING)
                    .orderIndex(orderIndex)
                    .createdAt(LocalDateTime.now())
                    .interrogation(interrogation)
                    .audioRecords(new ArrayList<>())
                    .build();
            interrogation.getOtherAudios().add(otherAudio);
        }

        CaseInterrogationAudioRecord record = CaseInterrogationAudioRecord.builder()
                .audioFileUrl(audioUrl)
                .transcribedText(null)
                .status(QAStatusEnum.TRANSCRIBING)
                .createdAt(LocalDateTime.now())
                .otherAudio(otherAudio)
                .build();
        otherAudio.getAudioRecords().add(record);

        CaseInterrogationAudioRecord savedRecord = audioRecordRepository.saveAndFlush(record);
        Long recordId = savedRecord.getId();
        String caseNumber = interrogation.getCaseEntity().getNumber();

        audioQueueService.sendAudioForProcessing(AudioProcessingMessage.builder()
                .interrogationId(interrogationId)
                .qaId(otherAudio.getId())
                .recordId(recordId)
                .caseNumber(caseNumber)
                .audioFileUrl(audioUrl)
                .originalFileName(originalFileName)
                .language(language)
                .email(email)
                .fieldName(fieldName)
                .build());

        logService.log(
                String.format("Uploading additional audio %s by %s user in case %s", audioUrl, email, caseNumber),
                LogLevel.INFO, LogAction.AUDIO_UPLOADED, caseNumber, email);

        return mapper.mapToOtherAudioResponse(otherAudio);
    }

    public record AudioUploadContext(String caseNumber, String fio, String language) {}
}