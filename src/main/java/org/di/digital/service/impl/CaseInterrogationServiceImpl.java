package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.constants.MessageConstant;
import org.di.digital.dto.message.AudioProcessingMessage;
import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.EditAudioTranscribedTextRequest;
import org.di.digital.dto.request.UpdateProtocolFieldRequest;
import org.di.digital.dto.response.*;
import org.di.digital.model.*;
import org.di.digital.model.enums.*;
import org.di.digital.model.fl.FLAddress;
import org.di.digital.model.fl.FLRecord;
import org.di.digital.repository.*;
import org.di.digital.service.CaseInterrogationService;
import org.di.digital.service.FLService;
import org.di.digital.service.LogService;
import org.di.digital.service.MinioService;
import org.di.digital.service.impl.queue.AudioQueueService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.Mapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.di.digital.util.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationServiceImpl implements CaseInterrogationService {
    private final CaseRepository caseRepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseInterrogationEducationRepository caseInterrogationEducationRepository;
    private final InterrogationChatRepository interrogationChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final AudioQueueService audioQueueService;
    private final LogService logService;
    private final TaskQueueService taskQueueService;
    private final FLService flService;
    private final Mapper mapper;

    @Transactional(readOnly = true)
    public List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, Boolean isDop, LocalDate date, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        return caseEntity.getInterrogations().stream()
                .filter(i -> role.equals("Все") || (i.getRole() != null && i.getRole().equalsIgnoreCase(role)))
                .filter(i -> fio == null || (i.getFio() != null && i.getFio().toLowerCase().contains(fio.toLowerCase())))
                .filter(i -> isDop == null || Objects.equals(i.getIsDop(), isDop))
                .filter(i -> date == null || i.getDate().equals(date))
                .map(mapper::mapToInterrogationResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CaseInterrogationFullResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        if (!caseEntity.isAtLeastOneFileProcessed()) {
            throw new IllegalStateException(MessageConstant.NO_FILE_PROCESSED.format(caseEntity.getNumber()));}

        if(caseEntity.getIsFinalIndictmentDone() != null && caseEntity.getIsFinalIndictmentDone()){
            throw new IllegalStateException(MessageConstant.CANNOT_CREATE_INTERROGATION.format(caseEntity.getNumber()));
        }

        String caseNumber = caseEntity.getNumber();
        LocalDateTime now = LocalDateTime.now();

        InterrogationTimerSession session = InterrogationTimerSession.builder()
                .startedAt(now)
                .build();

        FLRecord flRecord = null;
        FLAddress flAddress = null;
        try {
            flRecord = flService.getInfoByDocument(request.getDocumentType(), request.getNumber());
            flAddress = flService.getRegAddressAbout(flRecord.getIin());
        } catch (Exception e) {
            log.warn("Could not fetch FL data for document {}: {}", request.getNumber(), e.getMessage());
        }

        CaseInterrogationProtocol protocol = CaseInterrogationProtocol.builder()
                .fio(flRecord != null ? flRecord.getFio() : request.getFio())
                .dateOfBirth(flRecord != null && flRecord.getBirthDate() != null
                        ? flRecord.getBirthDate().toString() : null)
                .birthPlace(flRecord != null ? flRecord.getBirthRegion() : null)
                .citizenship(flRecord != null ? flRecord.getCitizenship() : null)
                .nationality(flRecord != null ? flRecord.getNationality() : null)
                .address(flAddress != null ? flAddress.getAddress() : null)
                .iinOrPassport(request.getNumber())
                .build();

        boolean isDop = caseEntity.getInterrogations().stream()
                .anyMatch(i -> request.getNumber().equals(i.getNumber())
                        && request.getRole().equals(i.getRole()));

        CaseInterrogation interrogation = CaseInterrogation.builder()
                .number(request.getNumber())
                .documentType(request.getDocumentType())
                .fio(request.getFio())
                .role(request.getRole())
                .date(LocalDate.now())
                .caseEntity(caseEntity)
                .isDop(isDop)
                .protocol(protocol)
                .status(CaseInterrogationStatusEnum.IN_PROGRESS)
                .startedAt(now)
                .timerSessions(new ArrayList<>(List.of(session)))
                .build();

        session.setInterrogation(interrogation);

        caseEntity.getInterrogations().add(interrogation);
        Case savedCase = caseRepository.save(caseEntity);

        CaseInterrogation saved = savedCase.getInterrogations().stream()
                .filter(i -> request.getFio().equals(i.getFio()) && now.equals(i.getStartedAt()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to retrieve saved interrogation"));
        log.info("Interrogation added to case: {}", caseId);


        logService.log(
                String.format("Interrogation %s to %s added by user %s", saved.getFio(),caseNumber, email),
                LogLevel.INFO,
                LogAction.INTERROGATION_ADDED,
                caseNumber,
                user.getEmail()
        );
        return mapper.mapToInterrogationFullResponse(saved, user);
    }

    @Override
    public void deleteInterrogation(Long caseId, Long interrogationId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found with id: " + interrogationId));
        caseEntity.removeInterrogation(interrogation);
        caseRepository.save(caseEntity);
        log.info("Interrogation removed from case: {}", caseId);

        logService.log(
                String.format("Deleting interrogation %s by %s user in case %s", interrogation.getFio(), email, caseNumber),
                LogLevel.INFO,
                LogAction.INTERROGATION_DELETED,
                caseNumber,
                email
        );
    }

    @Transactional
    public void updateProtocolField(Long caseId, Long interrogationId,
                                    UpdateProtocolFieldRequest request, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));


        CaseInterrogationProtocol protocol = interrogation.getProtocol();
        if (protocol == null) {
            protocol = new CaseInterrogationProtocol();
            interrogation.setProtocol(protocol);
        }

        switch (request.getField()) {
            case "fio"            -> protocol.setFio(request.getValue());
            case "dateOfBirth"    -> protocol.setDateOfBirth(request.getValue());
            case "birthPlace"     -> protocol.setBirthPlace(request.getValue());
            case "citizenship"    -> protocol.setCitizenship(request.getValue());
            case "nationality"    -> protocol.setNationality(request.getValue());
            case "education" -> {
                CaseInterrogationEducation education;
                Long eduId = request.getEducationId();
                if (eduId != null) {
                    education = caseInterrogationEducationRepository.findById(eduId)
                            .orElseThrow(() -> new RuntimeException("Education not found: " + eduId));
                    education.setEdu(request.getEducationEdu());
                    education.setType(request.getEducationType());
                } else {
                    education = CaseInterrogationEducation.builder()
                            .type(request.getEducationType())
                            .edu(request.getEducationEdu())
                            .protocol(protocol)
                            .build();
                    protocol.getEducations().add(education);
                }
            }
            case "martialStatus"  -> protocol.setMartialStatus(request.getValue());
            case "workOrStudyPlace"-> protocol.setWorkOrStudyPlace(request.getValue());
            case "position"       -> protocol.setPosition(request.getValue());
            case "address"        -> protocol.setAddress(request.getValue());
            case "contactPhone"       -> protocol.setContactPhone(request.getValue());
            case "contactEmail"       -> protocol.setContactEmail(request.getValue());
            case "military"       -> protocol.setMilitary(request.getValue());
            case "criminalRecord" -> protocol.setCriminalRecord(request.getValue());
            case "iinOrPassport"  -> protocol.setIinOrPassport(request.getValue());
            case "other"           -> protocol.setOther(request.getValue());
            case "relation"        -> protocol.setRelation(request.getValue());
            case "technical"        -> protocol.setTechnical(request.getValue());
            default -> throw new IllegalArgumentException("Unknown field: " + request.getField());
        }
    }

    @Transactional
    public void updateOtherField(Long caseId, Long interrogationId,
                                    UpdateProtocolFieldRequest request, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        switch (request.getField()) {
            case "city"             -> interrogation.setCity(request.getValue());
            case "personTranslator" -> interrogation.setPersonTranslator(request.getValue());
            case "personSpecialist" -> interrogation.setPersonSpecialist(request.getValue());
            case "personYear"       -> interrogation.setPersonYear(request.getValue());
            case "state"            -> interrogation.setState(request.getValue());
            case "investigatorProfession" -> interrogation.setInvestigatorProfession(request.getValue());
            case "investigatorRegion" -> interrogation.setInvestigatorRegion(request.getValue());
            case "caseNumberState"       -> interrogation.setCaseNumberState(request.getValue());
            case "room"            -> interrogation.setRoom(request.getValue());
            case "addrezz"          -> interrogation.setAddrezz(request.getValue());
            case "notificationNumber"    -> interrogation.setNotificationNumber(request.getValue());
            case "notificationDate"     -> interrogation.setNotificationDate(request.getValue());
            case "involved"    -> interrogation.setInvolved(request.getValue());
            case "involvedPersons" -> interrogation.setInvolvedPersons(request.getValue());
            case "confession"    -> interrogation.setConfession(request.getValue());
            case "confessionText" -> interrogation.setConfessionText(request.getValue());
            case "language"      -> interrogation.setLanguage(request.getValue());
            case "translator"  -> interrogation.setTranslator(request.getValue());
            case "defender"-> interrogation.setDefender(request.getValue());
            case "familiarization"       -> interrogation.setFamiliarization(request.getValue());
            case "additionalInfo"        -> interrogation.setAdditionalInfo(request.getValue());
            case "additionalText"       -> interrogation.setAdditionalText(request.getValue());
            case "application"       -> interrogation.setApplication(request.getValue());

            default -> throw new IllegalArgumentException("Unknown field: " + request.getField());
        }
    }
    @Transactional
    public QAResponse uploadAudioAndEnqueue(Long caseId, Long interrogationId, String question,
                                            MultipartFile file, String language, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        String audioUrl = minioService.uploadAudio(file, caseEntity.getNumber(), interrogation.getFio());

        int orderIndex = interrogation.getQaList().size();

        CaseInterrogationQA qa = CaseInterrogationQA.builder()
                .question(question)
                .audioFileUrl(audioUrl)
                .status(QAStatusEnum.TRANSCRIBING)
                .orderIndex(orderIndex)
                .isEdited(false)
                .createdAt(LocalDateTime.now())
                .interrogation(interrogation)
                .build();

        interrogation.getQaList().add(qa);
        CaseInterrogation savedInterrogation = caseInterrogationRepository.save(interrogation);

        Long qaId = savedInterrogation.getQaList().stream()
                .filter(q -> q.getOrderIndex() == orderIndex)
                .findFirst()
                .orElseThrow()
                .getId();

        audioQueueService.sendAudioForProcessing(AudioProcessingMessage.builder()
                .interrogationId(interrogationId)
                .qaId(qaId)
                .caseNumber(caseEntity.getNumber())
                .audioFileUrl(audioUrl)
                .originalFileName(file.getOriginalFilename())
                .language(language)
                .email(email)
                .fieldName(null)
                .build());

        return QAResponse.builder()
                .id(qaId)
                .question(question)
                .answer(null)
                .orderIndex(orderIndex)
                .edited(false)
                .status(QAStatusEnum.TRANSCRIBING)
                .build();
    }


    @Override
    @Transactional
    public OtherAudioResponse uploadOtherAudioAndEnqueue(Long caseId, Long interrogationId, String fieldName,
                                            MultipartFile file, String language, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        String audioUrl = minioService.uploadAudio(file, caseEntity.getNumber(), interrogation.getFio());

        int orderIndex = interrogation.getOtherAudios().size();

        CaseInterrogationOtherAudio text = CaseInterrogationOtherAudio.builder()
                .text(null)
                .audioFileUrl(audioUrl)
                .status(QAStatusEnum.TRANSCRIBING)
                .orderIndex(orderIndex)
                .createdAt(LocalDateTime.now())
                .interrogation(interrogation)
                .build();

        interrogation.getOtherAudios().add(text);
        CaseInterrogation savedInterrogation = caseInterrogationRepository.save(interrogation);

        Long otherId = savedInterrogation.getOtherAudios().stream()
                .filter(q -> q.getOrderIndex() == orderIndex)
                .findFirst()
                .orElseThrow()
                .getId();

        audioQueueService.sendAudioForProcessing(AudioProcessingMessage.builder()
                .interrogationId(interrogationId)
                .qaId(otherId)
                .caseNumber(caseEntity.getNumber())
                .audioFileUrl(audioUrl)
                .originalFileName(file.getOriginalFilename())
                .language(language)
                .email(email)
                .fieldName(fieldName)
                .build());

        return OtherAudioResponse.builder()
                .id(otherId)
                .fieldName(fieldName)
                .text(null)
                .status(QAStatusEnum.TRANSCRIBING)
                .build();
    }

    @Override
    @Transactional
    public QAResponse editTranscribedText(Long caseId, Long interrogationId, EditAudioTranscribedTextRequest request, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Interrogation does not belong to case: " + caseId);
        }

        CaseInterrogationQA qa = interrogation.getQaList().stream()
                .filter(q -> q.getId().equals(request.getQaId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("QA not found: " + request.getQaId()));

        qa.setAnswer(request.getAnswer());

        boolean assistantReplied = interrogationChatRepository.findByInterrogationId(interrogationId)
                .map(chat -> chat.getMessages().stream()
                        .anyMatch(m -> m.getRole() == MessageRole.ASSISTANT && m.isComplete()))
                .orElse(false);

        qa.setIsEdited(assistantReplied);

        if (assistantReplied) {
            interrogationChatRepository.findByInterrogationId(interrogationId)
                    .ifPresent(chat -> {
                        chatMessageRepository.findByInterrogationChatId(chat.getId(), PageRequest.of(0, Integer.MAX_VALUE))
                                .getContent()
                                .stream()
                                .filter(m -> m.getRole() == MessageRole.USER
                                        && m.getContent() != null
                                        && m.getContent().contains(qa.getQuestion()))
                                .findFirst()
                                .ifPresent(m -> {
                                    m.setIsEdited(true);
                                    m.setContent("Вопрос: " + qa.getQuestion() + "\n" + "Ответ: " + request.getAnswer());
                                    chatMessageRepository.save(m);
                                });
                    });
        }
        qa.setStatus(QAStatusEnum.TRANSCRIBED);
        caseInterrogationRepository.save(interrogation);

        return QAResponse.builder()
                .id(qa.getId())
                .question(qa.getQuestion())
                .answer(qa.getAnswer())
                .orderIndex(qa.getOrderIndex())
                .status(qa.getStatus())
                .edited(assistantReplied)
                .build();
    }

    @Transactional(readOnly = true)
    public List<QAResponse> getQAList(Long caseId, Long interrogationId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        return interrogation.getQaList().stream()
                .map(mapper::mapToQAResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CaseInterrogationFullResponse getDetailed(long caseId, long interrogationId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Interrogation does not belong to case: " + caseId);
        }

        return mapper.mapToInterrogationFullResponse(interrogation, user);
    }

    @Override
    public void completeInterrogation(long caseId, long interrogationId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Interrogation does not belong to case: " + caseId);
        }

        String caseNumber = interrogation.getCaseEntity().getNumber();
        LocalDateTime now = LocalDateTime.now();

        if (interrogation.getStartedAt() != null) {
            // Если таймер был запущен (не на паузе) — закрываем активную сессию
            if (!Boolean.TRUE.equals(interrogation.getIsPaused())) {
                InterrogationTimerSession lastSession = interrogation.getTimerSessions().stream()
                        .filter(s -> s.getPausedAt() == null)
                        .max(Comparator.comparing(InterrogationTimerSession::getStartedAt))
                        .orElse(null);

                if (lastSession != null) {
                    lastSession.setPausedAt(now);
                    long sessionSeconds = ChronoUnit.SECONDS.between(lastSession.getStartedAt(), now);
                    long accumulated = interrogation.getAccumulatedSeconds() == null
                            ? 0 : interrogation.getAccumulatedSeconds();
                    interrogation.setAccumulatedSeconds(accumulated + sessionSeconds);
                    interrogation.setDurationSeconds(interrogation.getAccumulatedSeconds());
                }
            }

            // finishedAt ставим только здесь — при реальном завершении
            interrogation.setFinishedAt(now);
            interrogation.setIsPaused(false);
        }

        interrogation.setStatus(CaseInterrogationStatusEnum.COMPLETED);
        caseInterrogationRepository.save(interrogation);

        logService.log(
                String.format("Completing interrogation %s by %s user in case %s", interrogation.getFio(), email, caseNumber),
                LogLevel.INFO,
                LogAction.INTERROGATION_COMPLETED,
                caseNumber,
                email
        );
    }
    @Transactional
    public void controlTimer(Long caseId, Long interrogationId, String action, String email) {
        log.info("controlTimer called: caseId={}, interrogationId={}, action={}, email={}",
                caseId, interrogationId, action, email);

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        log.info("Interrogation loaded: id={}, startedAt={}, finishedAt={}, accumulatedSeconds={}, timerSessionsSize={}",
                interrogation.getId(),
                interrogation.getStartedAt(),
                interrogation.getFinishedAt(),
                interrogation.getAccumulatedSeconds(),
                interrogation.getTimerSessions() == null ? "NULL" : interrogation.getTimerSessions().size());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Interrogation does not belong to case: " + caseId);
        }

        LocalDateTime now = LocalDateTime.now();

        if ("start".equals(action)) {
            boolean isRunning = interrogation.getStartedAt() != null
                    && !Boolean.TRUE.equals(interrogation.getIsPaused())
                    && interrogation.getStatus() != CaseInterrogationStatusEnum.COMPLETED;

            if (isRunning) {
                throw new IllegalStateException("Timer is already running");
            }

            if (interrogation.getStartedAt() == null) {
                interrogation.setStartedAt(now);
            }

            interrogation.setIsPaused(false);

            InterrogationTimerSession session = InterrogationTimerSession.builder()
                    .interrogation(interrogation)
                    .startedAt(now)
                    .build();
            interrogation.getTimerSessions().add(session);

        } else if ("pause".equals(action)) {
            boolean isNotRunning = interrogation.getStartedAt() == null
                    || Boolean.TRUE.equals(interrogation.getIsPaused())
                    || interrogation.getStatus() == CaseInterrogationStatusEnum.COMPLETED;

            if (isNotRunning) {
                throw new IllegalStateException("Timer is not running");
            }

            InterrogationTimerSession lastSession = interrogation.getTimerSessions().stream()
                    .filter(s -> s.getPausedAt() == null)
                    .max(Comparator.comparing(InterrogationTimerSession::getStartedAt))
                    .orElseThrow(() -> new IllegalStateException("No active timer session found"));

            lastSession.setPausedAt(now);

            long sessionSeconds = ChronoUnit.SECONDS.between(lastSession.getStartedAt(), now);
            long accumulated = interrogation.getAccumulatedSeconds() == null
                    ? 0 : interrogation.getAccumulatedSeconds();
            interrogation.setAccumulatedSeconds(accumulated + sessionSeconds);
            interrogation.setDurationSeconds(interrogation.getAccumulatedSeconds());

            interrogation.setIsPaused(true);
        } else {
            log.warn("Unknown action: {}", action);
            throw new IllegalArgumentException("Unknown action: " + action);
        }

        CaseInterrogation saved = caseInterrogationRepository.save(interrogation);
        log.info("Saved interrogation: id={}, timerSessionsSize={}, startedAt={}, finishedAt={}, durationSeconds={}",
                saved.getId(),
                saved.getTimerSessions().size(),
                saved.getStartedAt(),
                saved.getFinishedAt(),
                saved.getDurationSeconds());
    }
    @Transactional
    public List<CaseInterrogationApplicationFileResponse> uploadApplicationFiles(
            Long caseId, Long interrogationId, List<MultipartFile> files, String email) {

        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        Case caseEntity = interrogation.getCaseEntity();

        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();

        Set<String> existingInInterrogation = interrogation.getApplicationFiles().stream()
                .map(CaseInterrogationApplicationFile::getOriginalFileName)
                .collect(Collectors.toSet());

        Set<String> existingInCase = caseEntity.getFiles().stream()
                .map(CaseFile::getOriginalFileName)
                .collect(Collectors.toSet());

        List<CaseInterrogationApplicationFile> uploadedFiles = new ArrayList<>();
        List<CaseFile> uploadedCaseFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();

            if (existingInInterrogation.contains(originalName)) {
                log.warn("File already exists in interrogation {}: {}", interrogationId, originalName);
                continue;
            }

            try {
                CaseInterrogationApplicationFile appFile = minioService.uploadApplicationFile(
                        file,
                        caseEntity.getNumber(),
                        interrogation.getFio()
                );
                appFile.addInterrogation(interrogation);
                uploadedFiles.add(appFile);
                existingInInterrogation.add(originalName);

                if (!existingInCase.contains(originalName)) {
                    CaseFile caseFile = CaseFile.builder()
                            .originalFileName(appFile.getOriginalFileName())
                            .storedFileName(appFile.getStoredFileName())
                            .fileUrl(appFile.getFileUrl())
                            .contentType(appFile.getContentType())
                            .fileSize(appFile.getFileSize())
                            .uploadedAt(appFile.getUploadedAt())
                            .status(CaseFileStatusEnum.QUEUED)
                            .isQualification(false)
                            .build();
                    caseFile.addCaseEntity(caseEntity);
                    uploadedCaseFiles.add(caseFile);
                    existingInCase.add(originalName);
                }

            } catch (Exception e) {
                log.error("Failed to upload application file: {} for interrogation: {}",
                        originalName, interrogationId, e);
            }
        }

        caseInterrogationRepository.saveAndFlush(interrogation);

        for (CaseFile caseFile : uploadedCaseFiles) {
            taskQueueService.addTaskToQueue(
                    email,
                    caseEntity.getId(),
                    caseEntity.getNumber(),
                    caseFile.getOriginalFileName(),
                    caseFile.getFileUrl(),
                    caseFile.getId()
            );
        }

        log.info("Uploaded {}/{} application files for interrogation: {}",
                uploadedFiles.size(), files.size(), interrogationId);


        String fileNames = uploadedFiles.stream()
                .map(CaseInterrogationApplicationFile::getOriginalFileName)
                .collect(Collectors.joining(", "));

        logService.log(
                String.format("Uploading application files [%s] to interrogation №%d (FIO: %s) by user %s in case №%s",
                        fileNames, interrogationId, interrogation.getFio(), email, caseNumber),
                LogLevel.INFO,
                LogAction.FILE_UPLOAD,
                caseNumber,
                email
        );
        return uploadedFiles.stream()
                .map(mapper::mapToApplicationFileResponse)
                .toList();
    }
    @Transactional
    public void deleteApplicationFile(Long caseId, Long interrogationId, Long fileId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        CaseInterrogationApplicationFile file = interrogation.getApplicationFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

        minioService.deleteFile(file.getFileUrl());
        interrogation.getApplicationFiles().remove(file);
        caseInterrogationRepository.save(interrogation);
    }
}
