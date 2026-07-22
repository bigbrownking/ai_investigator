package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.*;
import org.di.digital.model.cases.Case;
import org.di.digital.dto.request.interrogation.AddInterrogationRequest;
import org.di.digital.dto.request.interrogation.EditAudioTranscribedTextRequest;
import org.di.digital.dto.request.interrogation.UpdateProtocolFieldRequest;
import org.di.digital.model.enums.*;
import org.di.digital.model.fl.FLAddress;
import org.di.digital.model.fl.FLRecord;
import org.di.digital.model.interrogation.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.*;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.interrogation.CaseInterrogationService;
import org.di.digital.service.FLService;
import org.di.digital.service.LogService;
import org.di.digital.service.core.MinioService;
import org.di.digital.util.Mapper;
import org.di.digital.util.PageCounter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


import static org.di.digital.util.requests.RequestUrlBuilder.caseInfoUrl;
import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationServiceImpl implements CaseInterrogationService {
    private final CaseRepository caseRepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseInterrogationEducationRepository caseInterrogationEducationRepository;
    private final CaseInterrogationMilitaryRepository caseInterrogationMilitaryRepository;
    private final CaseInterrogationCriminalRepository caseInterrogationCriminalRepository;
    private final CaseInterrogationRelationRepository caseInterrogationRelationRepository;
    private final CaseInterrogationInvolvedPersonsRepository caseInterrogationInvolvedPersonsRepository;
    private final CaseInterrogationChatRepository caseInterrogationChatRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final LogService logService;
    private final FLService flService;
    private final Mapper mapper;
    private final WebClient.Builder webClientBuilder;
    private final PageCounter pageCounter;
    private final InterrogationTimeGuard timeGuard;
    private final InterrogationCreateWriter interrogationWriter;
    private final AudioUploadWriter audioUploadWriter;
    private final ApplicationFileWriter applicationFileWriter;

    @Value("${model.host}")
    private String pythonHost;

    @Value("${tree.port}")
    private String treePort;

    @Transactional(readOnly = true)
    public List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, Boolean isDop, LocalDate date, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        return caseEntity.getInterrogations().stream()
                .filter(i -> role.equals("Все") || (i.getRole() != null && i.getRole().equalsIgnoreCase(role)))
                .filter(i -> fio == null || (i.getFio() != null && i.getFio().toLowerCase().contains(fio.toLowerCase())))
                .filter(i -> isDop == null || Objects.equals(i.getIsDop(), isDop))
                .filter(i -> date == null || i.getDate().equals(date))
                .map(mapper::mapToInterrogationResponse)
                .collect(Collectors.toList());
    }

    public CaseInterrogationFullResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email) {
        LocalDateTime now = LocalDateTime.now();

        FLRecord flRecord = null;
        FLAddress flAddress = null;
        try {
            flRecord = flService.getInfoByDocument(
                    request.getDocumentType(), request.getNumber(), request.getLanguage());
            flAddress = flService.getRegAddressAbout(flRecord.getIin(), request.getLanguage());
        } catch (Exception e) {
            log.warn("Could not fetch FL data for document {}: {}", request.getNumber(), e.getMessage());
        }

        String caseNumber = interrogationWriter.getCaseNumber(caseId);
        String article = fetchArticleFromCaseInfo(caseNumber);

        return interrogationWriter.createInterrogation(
                caseId, email, request, now, flRecord, flAddress, article);
    }
    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "г.";
    }

    private String fetchArticleFromCaseInfo(String caseNumber) {
        try {
            String url = caseInfoUrl(pythonHost, treePort, caseNumber);
            return webClientBuilder.build()
                    .post()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(body -> (String) body.get("article"))
                    .block();
        } catch (Exception e) {
            log.warn("Could not fetch case info for {}: {}", caseNumber, e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public QAResponse createQA(Long caseId, Long interrogationId, String question, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        int orderIndex = interrogation.getQaList().size();

        CaseInterrogationQA qa = CaseInterrogationQA.builder()
                .question(question)
                .answer(null)
                .status(QAStatusEnum.PENDING)
                .orderIndex(orderIndex)
                .isEdited(false)
                .isReformulated(false)
                .createdAt(LocalDateTime.now())
                .interrogation(interrogation)
                .audioRecords(new ArrayList<>())
                .build();

        interrogation.getQaList().add(qa);
        CaseInterrogation saved = caseInterrogationRepository.save(interrogation);

        CaseInterrogationQA savedQa = saved.getQaList().stream()
                .filter(q -> q.getOrderIndex().equals(orderIndex))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("QA не найден после сохранения"));

        logService.log(
                String.format("QA created in interrogation %d by %s in case %s",
                        interrogationId, email, caseEntity.getNumber()),
                LogLevel.INFO,
                LogAction.QA_CREATED,
                caseEntity.getNumber(),
                email
        );

        return mapper.mapToQAResponse(savedQa);
    }
    @Override
    @Transactional
    public void deleteInterrogation(Long caseId, Long interrogationId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        String caseNumber = caseEntity.getNumber();
        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));
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
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));


        CaseInterrogationProtocol protocol = interrogation.getProtocol();
        if (protocol == null) {
            protocol = new CaseInterrogationProtocol();
            interrogation.setProtocol(protocol);
        }

        switch (request.getField()) {
            case "fio" -> protocol.setFio(request.getValue());
            case "dateOfBirth" -> protocol.setDateOfBirth(request.getValue());
            case "birthPlace" -> protocol.setBirthPlace(request.getValue());
            case "citizenship" -> protocol.setCitizenship(request.getValue());
            case "nationality" -> protocol.setNationality(request.getValue());
            case "education" -> {
                CaseInterrogationEducation education;
                Long eduId = request.getId();
                if (eduId != null) {
                    education = caseInterrogationEducationRepository.findById(eduId)
                            .orElseThrow(() -> new IllegalStateException("Образование не найдено: " + eduId));
                    education.setAbout(request.getAbout());
                    education.setType(request.getType());
                } else {
                    education = CaseInterrogationEducation.builder()
                            .type(request.getType())
                            .about(request.getAbout())
                            .protocol(protocol)
                            .build();
                    protocol.getEducations().add(education);
                }
            }
            case "martialStatus" -> protocol.setMartialStatus(request.getValue());
            case "workOrStudyPlace" -> protocol.setWorkOrStudyPlace(request.getValue());
            case "position" -> protocol.setPosition(request.getValue());
            case "address" -> protocol.setAddress(request.getValue());
            case "contactPhone" -> protocol.setContactPhone(request.getValue());
            case "contactEmail" -> protocol.setContactEmail(request.getValue());
            case "military" -> {
                CaseInterrogationMilitaryRecord militaryRecord;
                Long militaryId = request.getId();
                if (militaryId != null) {
                    militaryRecord = caseInterrogationMilitaryRepository.findById(militaryId)
                            .orElseThrow(() -> new IllegalStateException("Воинский учет не найден: " + militaryId));
                    militaryRecord.setAbout(request.getAbout());
                    militaryRecord.setType(request.getType());
                } else {
                    militaryRecord = CaseInterrogationMilitaryRecord.builder()
                            .type(request.getType())
                            .about(request.getAbout())
                            .protocol(protocol)
                            .build();
                    protocol.getMilitaries().add(militaryRecord);
                }
            }
            case "criminalRecord" -> {
                CaseInterrogationCriminalRecord criminalRecord;
                Long criminalId = request.getId();
                if (criminalId != null) {
                    criminalRecord = caseInterrogationCriminalRepository.findById(criminalId)
                            .orElseThrow(() -> new IllegalStateException("Судимость не найдена: " + criminalId));
                    criminalRecord.setAbout(request.getAbout());
                    criminalRecord.setType(request.getType());
                } else {
                    criminalRecord = CaseInterrogationCriminalRecord.builder()
                            .type(request.getType())
                            .about(request.getAbout())
                            .protocol(protocol)
                            .build();
                    protocol.getCriminals().add(criminalRecord);
                }
            }
            case "iinOrPassport" -> protocol.setIinOrPassport(request.getValue());
            case "other" -> protocol.setOther(request.getValue());
            case "relation" -> {
                CaseInterrogationRelationRecord relationRecord;
                Long relationId = request.getId();
                if (relationId != null) {
                    relationRecord = caseInterrogationRelationRepository.findById(relationId)
                            .orElseThrow(() -> new IllegalStateException("Отношение не найдено: " + relationId));
                    relationRecord.setAbout(request.getAbout());
                    relationRecord.setType(request.getType());
                } else {
                    relationRecord = CaseInterrogationRelationRecord.builder()
                            .type(request.getType())
                            .about(request.getAbout())
                            .protocol(protocol)
                            .build();
                    protocol.getRelationRecords().add(relationRecord);
                }
            }
            case "technical" -> protocol.setTechnical(request.getValue());
            default -> throw new IllegalArgumentException("Unknown field: " + request.getField());
        }
    }

    @Transactional
    public void updateOtherField(Long caseId, Long interrogationId,
                                 UpdateProtocolFieldRequest request, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        switch (request.getField()) {
            case "city" -> interrogation.setCity(request.getValue());
            case "lawyer" -> interrogation.setLawyer(request.getValue());
            case "personTranslator" -> interrogation.setPersonTranslator(request.getValue());
            case "personSpecialist" -> interrogation.setPersonSpecialist(request.getValue());
            case "personYear" -> interrogation.setPersonYear(request.getValue());
            case "state" -> interrogation.setState(request.getValue());
            case "investigatorProfession" -> interrogation.setInvestigatorProfession(request.getValue());
            case "investigatorAdministration" -> interrogation.setInvestigatorAdministration(request.getValue());
            case "investigatorRegion" -> interrogation.setInvestigatorRegion(request.getValue());
            case "caseNumberState" -> interrogation.setCaseNumberState(request.getValue());
            case "room" -> interrogation.setRoom(request.getValue());
            case "addrezz" -> interrogation.setAddrezz(request.getValue());
            case "notificationNumber" -> interrogation.setNotificationNumber(request.getValue());
            case "notificationDate" -> interrogation.setNotificationDate(request.getValue());
            case "testimony" -> interrogation.setTestimony(request.getValue());
            case "involved" -> interrogation.setInvolved(request.getValue());

            case "involvedPersons" -> {
                CaseInterrogationInvolvedPersons involvedPersons;
                Long involvedPersonsId = request.getId();
                if (involvedPersonsId != null) {
                    involvedPersons = caseInterrogationInvolvedPersonsRepository.findById(involvedPersonsId)
                            .orElseThrow(() -> new IllegalStateException("Вовлеченные люди не найдены: " + involvedPersonsId));
                    involvedPersons.setAbout(request.getAbout());
                    involvedPersons.setType(request.getType());
                } else {
                    involvedPersons = CaseInterrogationInvolvedPersons.builder()
                            .type(request.getType())
                            .about(request.getAbout())
                            .interrogation(interrogation)
                            .build();
                    interrogation.getInvolvedPersons().add(involvedPersons);
                }

            }

            case "confession" -> interrogation.setConfession(request.getValue());
            case "confessionText" -> interrogation.setConfessionText(request.getValue());
            case "language" -> interrogation.setLanguage(request.getValue());
            case "translator" -> interrogation.setTranslator(request.getValue());
            case "defender" -> interrogation.setDefender(request.getValue());
            case "familiarization" -> interrogation.setFamiliarization(request.getValue());
            case "additionalInfo" -> interrogation.setAdditionalInfo(request.getValue());
            case "additionalText" -> interrogation.setAdditionalText(request.getValue());
            case "application" -> interrogation.setApplication(request.getValue());

            default -> throw new IllegalArgumentException("Unknown field: " + request.getField());
        }
    }

    @Override
    public QAResponse uploadAudioAndEnqueue(Long caseId, Long interrogationId, Long qaId,
                                            MultipartFile file, String email) {
        AudioUploadWriter.AudioUploadContext ctx =
                audioUploadWriter.validateForQaUpload(caseId, interrogationId, qaId, email, timeGuard);

        String audioUrl = minioService.uploadAudio(file, ctx.caseNumber(), ctx.fio());

        return audioUploadWriter.persistQaAudio(
                interrogationId, qaId, audioUrl, file.getOriginalFilename(), email);
    }

    @Override
    public OtherAudioResponse uploadOtherAudioAndEnqueue(Long caseId, Long interrogationId, Long otherAudioId,
                                                         String fieldName, MultipartFile file,
                                                         String language, String email) {
        AudioUploadWriter.AudioUploadContext ctx =
                audioUploadWriter.validateForOtherUpload(caseId, interrogationId, otherAudioId, email, timeGuard);

        String audioUrl = minioService.uploadAudio(file, ctx.caseNumber(), ctx.fio());

        return audioUploadWriter.persistOtherAudio(
                interrogationId, otherAudioId, fieldName, audioUrl,
                file.getOriginalFilename(), language, email);
    }
    @Override
    @Transactional
    public QAResponse editTranscribedText(Long caseId, Long interrogationId, EditAudioTranscribedTextRequest request, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Допрос не принадлежит делу: " + caseId);
        }

        CaseInterrogationQA qa = interrogation.getQaList().stream()
                .filter(q -> q.getId().equals(request.getQaId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Вопрос/ответ не найден: " + request.getQaId()));

        qa.setAnswer(request.getAnswer());
        qa.setManuallyEdited(true);

        boolean assistantReplied = caseInterrogationChatRepository.findByInterrogationId(interrogationId)
                .map(chat -> chat.getMessages().stream()
                        .anyMatch(m -> m.getRole() == MessageRole.ASSISTANT && m.isComplete()))
                .orElse(false);

        qa.setIsEdited(assistantReplied);

        if (assistantReplied && qa.getQuestion() != null) {
            caseInterrogationChatRepository.findByInterrogationId(interrogationId)
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

    @Override
    @Transactional
    public OtherAudioResponse editOtherAudioText(Long caseId, Long interrogationId, Long otherAudioId, String text, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Допрос не принадлежит делу: " + caseId);
        }

        CaseInterrogationOtherAudio otherAudio = interrogation.getOtherAudios().stream()
                .filter(o -> o.getId().equals(otherAudioId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Аудио не найдено: " + otherAudioId));

        otherAudio.setText(text);
        otherAudio.setManuallyEdited(true);
        otherAudio.setStatus(QAStatusEnum.TRANSCRIBED);
        caseInterrogationRepository.save(interrogation);

        return mapper.mapToOtherAudioResponse(otherAudio);
    }

    @Transactional(readOnly = true)
    public List<QAResponse> getQAList(Long caseId, Long interrogationId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        return interrogation.getQaList().stream()
                .map(mapper::mapToQAResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CaseInterrogationFullResponse getDetailed(long caseId, long interrogationId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Допрос не принадлежит делу: " + caseId);
        }

        return mapper.mapToInterrogationFullResponse(interrogation, user);
    }

    @Override
    @Transactional
    public void completeInterrogation(long caseId, long interrogationId, String email) {

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Допрос не принадлежит делу: " + caseId);
        }

        finishInterrogation(interrogation, LocalDateTime.now());

        logService.log(
                String.format(
                        "Completing interrogation %s by %s user in case %s",
                        interrogation.getFio(),
                        email,
                        interrogation.getCaseEntity().getNumber()
                ),
                LogLevel.INFO,
                LogAction.INTERROGATION_COMPLETED,
                interrogation.getCaseEntity().getNumber(),
                email
        );
    }
    @Transactional
    public void completeInterrogationByScheduler(CaseInterrogation interrogation) {

        finishInterrogation(interrogation, LocalDateTime.now());

        log.info(
                "Interrogation {} automatically completed due to time limit",
                interrogation.getId()
        );
    }

    @Transactional
    public void controlTimer(Long caseId, Long interrogationId, String action, String email) {
        log.info("controlTimer called: caseId={}, interrogationId={}, action={}, email={}",
                caseId, interrogationId, action, email);

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        log.info("Interrogation loaded: id={}, startedAt={}, finishedAt={}, accumulatedSeconds={}, timerSessionsSize={}",
                interrogation.getId(),
                interrogation.getStartedAt(),
                interrogation.getFinishedAt(),
                interrogation.getAccumulatedSeconds(),
                interrogation.getTimerSessions() == null ? "NULL" : interrogation.getTimerSessions().size());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Допрос не принадлежит делу: " + caseId);
        }

        LocalDateTime now = LocalDateTime.now();

        if ("start".equals(action)) {
            if (!Boolean.TRUE.equals(interrogation.getCategoryConfirmed())) {
                throw new IllegalStateException(
                        "Категория допроса не подтверждена — запуск таймера недоступен");
            }

            boolean resumingAfterBreak = false;

            if (Boolean.TRUE.equals(interrogation.getOnBreak())
                    && interrogation.getBreakStartedAt() != null) {
                Duration passed = Duration.between(interrogation.getBreakStartedAt(), now);
                if (passed.compareTo(CaseInterrogation.MANDATORY_BREAK) < 0) {
                    long left = CaseInterrogation.MANDATORY_BREAK.minus(passed).toMinutes();
                    throw new IllegalStateException("Перерыв ещё не завершён. Осталось: " + left + " мин.");
                }

                interrogation.setOnBreak(false);
                interrogation.setContinuousOverrideConfirmed(false);
                interrogation.setNotifiedContinuousWarn(false);
                interrogation.setNotifiedContinuousLimit(false);
                interrogation.setNotifiedBreakOver(false);
                resumingAfterBreak = true;
            }

            // ⬇️ СБРОС СЕРИИ ПЕРЕНЕСЁН СЮДА — ДО assertCanRecord
            // Новая серия должна существовать к моменту проверки, иначе assertCanRecord
            // посчитает старую (превышенную) серию и кинет 422 после перерыва.
            if (interrogation.getCurrentSeriesStartedAt() == null || resumingAfterBreak) {
                interrogation.setCurrentSeriesStartedAt(now);
                interrogation.setBreakStartedAt(null); // перерыв отработан
            }

            // Теперь проверка видит уже новую серию (continuous = 0 после перерыва)
            timeGuard.assertCanRecord(interrogation, now);

            if (interrogation.getStartedAt() == null) {
                interrogation.setStartedAt(now);
            }

            interrogation.setIsPaused(false);

            interrogation.getTimerSessions().add(CaseInterrogationTimerSession.builder()
                    .interrogation(interrogation)
                    .startedAt(now)
                    .build());

        }  else if ("pause".equals(action)) {
            boolean isNotRunning = interrogation.getStartedAt() == null
                    || Boolean.TRUE.equals(interrogation.getIsPaused())
                    || interrogation.getStatus() == CaseInterrogationStatusEnum.COMPLETED;

            if (isNotRunning) {
                throw new IllegalStateException("Таймер не запущен");
            }

            CaseInterrogationTimerSession lastSession = interrogation.getTimerSessions().stream()
                    .filter(s -> s.getPausedAt() == null)
                    .max(Comparator.comparing(CaseInterrogationTimerSession::getStartedAt))
                    .orElseThrow(() -> new IllegalStateException("Нет активной сессии таймера для паузы"));

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
    public List<CaseInterrogationApplicationFileResponse> uploadApplicationFiles(
            Long caseId, Long interrogationId, List<MultipartFile> files,
            Map<String, String> displayNames, String email) {

        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        ApplicationFileWriter.UploadContext ctx =
                applicationFileWriter.prepare(caseId, interrogationId, email);

        List<ApplicationFileWriter.UploadedFile> uploaded = new ArrayList<>();
        Set<String> seenInThisBatch = new HashSet<>(ctx.existingInInterrogation());

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String originalName = file.getOriginalFilename();

            if (seenInThisBatch.contains(originalName)) {
                log.warn("File already exists in interrogation {}: {}", interrogationId, originalName);
                continue;
            }

            try {
                CaseInterrogationApplicationFile appFile =
                        minioService.uploadApplicationFile(file, ctx.caseNumber(), ctx.fio());

                Integer pages = null;
                try {
                    pages = pageCounter.countPagesByUrl(appFile.getFileUrl(), appFile.getContentType());
                } catch (Exception e) {
                    log.warn("Could not count pages for {}: {}", appFile.getOriginalFileName(), e.getMessage());
                }

                String displayName = displayNames.getOrDefault(originalName, originalName);

                uploaded.add(new ApplicationFileWriter.UploadedFile(
                        appFile.getOriginalFileName(), appFile.getStoredFileName(), appFile.getFileUrl(),
                        appFile.getContentType(), appFile.getFileSize(), appFile.getUploadedAt(),
                        displayName, pages));
                seenInThisBatch.add(originalName);

            } catch (Exception e) {
                log.error("Failed to upload application file: {} for interrogation: {}", originalName, interrogationId, e);
            }
        }

        if (uploaded.isEmpty()) {
            return Collections.emptyList();
        }

        return applicationFileWriter.persist(interrogationId, email, ctx.language(), uploaded);
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

    @Transactional(readOnly = true)
    public InterrogationTimeStatusResponse getTimeStatus(Long caseId, Long interrogationId, String email) {
        CaseInterrogation interrogation = loadAndAuthorize(caseId, interrogationId, email);
        return timeGuard.status(interrogation, LocalDateTime.now());
    }

    /** Объявление перерыва (треб.1 → треб.2): фиксирует событие и запускает таймер отдыха. */
    @Transactional
    public InterrogationTimeStatusResponse startBreak(Long caseId, Long interrogationId, String email) {
        CaseInterrogation interrogation = loadAndAuthorize(caseId, interrogationId, email);
        LocalDateTime now = LocalDateTime.now();

        // Закрыть ВСЕ активные сессии (их может быть несколько)
        long addedSeconds = 0;
        for (CaseInterrogationTimerSession s : interrogation.getTimerSessions()) {
            if (s.getPausedAt() == null && s.getStartedAt() != null) {
                s.setPausedAt(now);
                addedSeconds += ChronoUnit.SECONDS.between(s.getStartedAt(), now);
            }
        }
        if (addedSeconds > 0) {
            long acc = interrogation.getAccumulatedSeconds() == null ? 0 : interrogation.getAccumulatedSeconds();
            interrogation.setAccumulatedSeconds(acc + addedSeconds);
            interrogation.setDurationSeconds(interrogation.getAccumulatedSeconds());
        }

        interrogation.setIsPaused(true);
        interrogation.setOnBreak(true);
        interrogation.setBreakStartedAt(now);
        interrogation.setContinuousOverrideConfirmed(false);
        interrogation.setNotifiedBreakOver(false);
        caseInterrogationRepository.save(interrogation);

        logService.log(
                String.format("Break started for interrogation %s by %s in case %s",
                        interrogation.getFio(), email, interrogation.getCaseEntity().getNumber()),
                LogLevel.INFO, LogAction.INTERROGATION_BREAK_STARTED,
                interrogation.getCaseEntity().getNumber(), email);

        return timeGuard.status(interrogation, now);
    }

    /** Подтверждение категории/обстоятельств пользователем (треб.5). */
    @Transactional
    public InterrogationTimeStatusResponse confirmCategory(Long caseId, Long interrogationId,
                                                           InterrogationSpecialGround ground,
                                                           String groundNote, String email) {
        if (ground == null) {
            throw new IllegalStateException("Категория не выбрана");
        }
        CaseInterrogation interrogation = loadAndAuthorize(caseId, interrogationId, email);
        if (Boolean.TRUE.equals(interrogation.getCategoryConfirmed())
                && interrogation.getStartedAt() != null) {
            throw new IllegalStateException("Категория уже подтверждена, допрос запущен");
        }
        InterrogationLimitProfile profile = ground.isSpecial()
                ? InterrogationLimitProfile.SPECIAL
                : InterrogationLimitProfile.STANDARD;

        interrogation.setLimitProfile(profile);
        interrogation.setSpecialGround(ground);
        interrogation.setSpecialGroundNote(
                ground == InterrogationSpecialGround.OTHER ? groundNote : null);
        interrogation.setCategoryConfirmed(true);
        caseInterrogationRepository.save(interrogation);

        logService.log(
                String.format("Category confirmed for interrogation %s by %s: profile=%s, ground=%s%s",
                        interrogation.getFio(), email, profile, ground,
                        ground == InterrogationSpecialGround.OTHER && groundNote != null
                                ? " (" + groundNote + ")" : ""),
                LogLevel.INFO,
                LogAction.INTERROGATION_CATEGORY_CONFIRMED,
                interrogation.getCaseEntity().getNumber(),
                email);

        return timeGuard.status(interrogation, LocalDateTime.now());
    }

    /** Подтверждение оснований продолжить сверх непрерывного лимита (треб.1). */
    @Transactional
    public InterrogationTimeStatusResponse confirmContinuousOverride(Long caseId, Long interrogationId, String email) {
        CaseInterrogation interrogation = loadAndAuthorize(caseId, interrogationId, email);
        LocalDateTime now = LocalDateTime.now();

        // Нельзя подтверждать override во время перерыва — сначала перерыв должен закончиться
        if (Boolean.TRUE.equals(interrogation.getOnBreak())) {
            throw new IllegalStateException(
                    "Идёт обязательный перерыв — продолжение доступно после его завершения");
        }

        // Нельзя, если допрос уже завершён
        if (interrogation.getStatus() == CaseInterrogationStatusEnum.COMPLETED) {
            throw new IllegalStateException("Допрос уже завершён");
        }

        interrogation.setContinuousOverrideConfirmed(true);

        // Возобновить счёт: если сессия стоит на паузе (её остановил шедулер на лимите) —
        // открыть новую сессию в ТОЙ ЖЕ серии (currentSeriesStartedAt не трогаем,
        // continuous продолжает расти за лимит — это и есть смысл override).
        boolean hasActiveSession = interrogation.getTimerSessions().stream()
                .anyMatch(s -> s.getPausedAt() == null && s.getStartedAt() != null);

        if (!hasActiveSession) {
            if (interrogation.getStartedAt() == null) {
                interrogation.setStartedAt(now);
            }
            // на всякий случай — серия должна существовать; если её нет, стартуем
            if (interrogation.getCurrentSeriesStartedAt() == null) {
                interrogation.setCurrentSeriesStartedAt(now);
            }
            interrogation.setIsPaused(false);
            interrogation.getTimerSessions().add(CaseInterrogationTimerSession.builder()
                    .interrogation(interrogation)
                    .startedAt(now)
                    .build());
        }

        caseInterrogationRepository.save(interrogation);

        logService.log(
                String.format("Continuous-limit override confirmed for %s by %s",
                        interrogation.getFio(), email),
                LogLevel.WARNING, LogAction.INTERROGATION_LIMIT_OVERRIDE,
                interrogation.getCaseEntity().getNumber(), email);

        return timeGuard.status(interrogation, now);
    }

    private CaseInterrogation loadAndAuthorize(Long caseId, Long interrogationId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
        validateUserAccess(interrogation.getCaseEntity(), user);
        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Допрос не принадлежит делу: " + caseId);
        }
        return interrogation;
    }
    private void finishInterrogation(CaseInterrogation interrogation, LocalDateTime now) {

        if (interrogation.getStartedAt() != null) {
            if (!Boolean.TRUE.equals(interrogation.getIsPaused())) {

                CaseInterrogationTimerSession lastSession = interrogation.getTimerSessions().stream()
                        .filter(s -> s.getPausedAt() == null)
                        .max(Comparator.comparing(CaseInterrogationTimerSession::getStartedAt))
                        .orElse(null);

                if (lastSession != null) {
                    lastSession.setPausedAt(now);

                    long sessionSeconds = ChronoUnit.SECONDS.between(lastSession.getStartedAt(), now);
                    long accumulated = interrogation.getAccumulatedSeconds() == null
                            ? 0L
                            : interrogation.getAccumulatedSeconds();

                    long total = accumulated + sessionSeconds;

                    interrogation.setAccumulatedSeconds(total);
                    interrogation.setDurationSeconds(total);
                }
            }

            interrogation.setFinishedAt(now);
            interrogation.setIsPaused(false);
        }

        interrogation.setOnBreak(false);
        interrogation.setStatus(CaseInterrogationStatusEnum.COMPLETED);

        caseInterrogationRepository.save(interrogation);
    }
}
