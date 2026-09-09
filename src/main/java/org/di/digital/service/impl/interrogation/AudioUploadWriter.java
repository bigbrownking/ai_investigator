package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.AudioProcessingMessage;
import org.di.digital.dto.response.interrogation.OtherAudioResponse;
import org.di.digital.dto.response.interrogation.QAResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.log.LogAction;
import org.di.digital.model.enums.log.LogLevel;
import org.di.digital.model.enums.interrogation.QAStatusEnum;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.interrogation.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationAudioRecordRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.service.impl.queue.AudioQueueService;
import org.di.digital.util.mapper.InterrogationMapper;
import org.di.digital.util.requests.UserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioUploadWriter {
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseInterrogationAudioRecordRepository audioRecordRepository;
    private final AudioQueueService audioQueueService;
    private final LogService logService;
    private final InterrogationMapper mapper;
    private final InterrogationAuthService interrogationAuthService;

    @Transactional(readOnly = true)
    public AudioUploadContext validateForQaUpload(Long caseId, Long interrogationId, Long qaId,
                                                  String email, InterrogationTimeGuard timeGuard) {
        InterrogationAuthService.AuthorizedInterrogation ctx = interrogationAuthService.loadAndAuthorize(caseId, interrogationId, email,
                CaseModule.INTERROGATION, CaseAction.UPDATE);
        CaseInterrogation interrogation = ctx.interrogation();
        Case caseEntity = interrogation.getCaseEntity();

        timeGuard.assertCanRecord(interrogation, LocalDateTime.now());

        interrogation.getQaList().stream()
                .filter(q -> q.getId().equals(qaId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.QA.localized(currentLang(), qaId.toString())));

        return new AudioUploadContext(caseEntity.getNumber(), interrogation.getFio(),
                interrogation.getLanguage());
    }

    @Transactional(readOnly = true)
    public AudioUploadContext validateForOtherUpload(Long caseId, Long interrogationId, Long otherAudioId,
                                                     String email, InterrogationTimeGuard timeGuard) {
        InterrogationAuthService.AuthorizedInterrogation ctx = interrogationAuthService.loadAndAuthorize(caseId, interrogationId, email,
                CaseModule.INTERROGATION, CaseAction.UPDATE);
        CaseInterrogation interrogation = ctx.interrogation();
        Case caseEntity = interrogation.getCaseEntity();

        timeGuard.assertCanRecord(interrogation, LocalDateTime.now());

        if (otherAudioId != null) {
            interrogation.getOtherAudios().stream()
                    .filter(o -> o.getId().equals(otherAudioId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.AUDIO.localized(currentLang(), otherAudioId.toString())));
        }

        return new AudioUploadContext(caseEntity.getNumber(), interrogation.getFio(),
                interrogation.getLanguage());
    }

    @Transactional
    public QAResponse persistQaAudio(Long interrogationId, Long qaId, String audioUrl,
                                     String originalFileName, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(currentLang(), interrogationId.toString())));

        CaseInterrogationQA qa = interrogation.getQaList().stream()
                .filter(q -> q.getId().equals(qaId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.QA.localized(currentLang(), qaId.toString())));

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

        return mapper.toShortQAResponse(qa);
    }

    // ---- Фаза 3 (Other): сохранить record, отправить в очередь, вернуть DTO ----
    @Transactional
    public OtherAudioResponse persistOtherAudio(Long interrogationId, Long otherAudioId,
                                                String fieldName, String audioUrl,
                                                String originalFileName, String language, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(currentLang(), interrogationId.toString())));

        CaseInterrogationOtherAudio otherAudio;
        if (otherAudioId != null) {
            otherAudio = interrogation.getOtherAudios().stream()
                    .filter(o -> o.getId().equals(otherAudioId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(NotFoundMessage.AUDIO.localized(currentLang(), otherAudioId.toString())));
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

        return mapper.toOtherAudioResponse(otherAudio);
    }

    public record AudioUploadContext(String caseNumber, String fio, String language) {}

    private UserSettingsLanguage currentLang() {
        return getCurrentLang();
    }
}