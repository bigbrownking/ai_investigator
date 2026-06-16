package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.*;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.dto.message.AudioProcessingMessage;
import org.di.digital.dto.request.interrogation.AddInterrogationRequest;
import org.di.digital.dto.request.interrogation.EditAudioTranscribedTextRequest;
import org.di.digital.dto.request.interrogation.UpdateProtocolFieldRequest;
import org.di.digital.model.enums.*;
import org.di.digital.model.fl.FLAddress;
import org.di.digital.model.fl.FLDocument;
import org.di.digital.model.fl.FLRecord;
import org.di.digital.model.interrogation.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.*;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.CaseInterrogationService;
import org.di.digital.service.FLService;
import org.di.digital.service.LogService;
import org.di.digital.service.MinioService;
import org.di.digital.service.impl.queue.AudioQueueService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.LocalizationHelper;
import org.di.digital.util.Mapper;
import org.di.digital.util.PageCounter;
import org.di.digital.util.requests.RequestUrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.nullToEmpty;
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
    private final AudioQueueService audioQueueService;
    private final LogService logService;
    private final TaskQueueService taskQueueService;
    private final FLService flService;
    private final Mapper mapper;
    private final WebClient.Builder webClientBuilder;
    private final LocalizationHelper localizationHelper;
    private final PageCounter pageCounter;


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

    @Transactional
    public CaseInterrogationFullResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        if (!caseEntity.isAtLeastOneFileProcessed()) {
            throw new IllegalStateException(MessageConstant.NO_FILE_PROCESSED.format(caseEntity.getNumber()));
        }

        if (caseEntity.getIsFinalIndictmentDone() != null && caseEntity.getIsFinalIndictmentDone()) {
            throw new IllegalStateException(MessageConstant.CANNOT_CREATE_INTERROGATION.format(caseEntity.getNumber()));
        }

        String caseNumber = caseEntity.getNumber();
        LocalDateTime now = LocalDateTime.now();

        CaseInterrogationTimerSession session = CaseInterrogationTimerSession.builder()
                .startedAt(now)
                .build();

        FLRecord flRecord = null;
        FLAddress flAddress = null;
        try {
            flRecord = flService.getInfoByDocument(request.getDocumentType(), request.getNumber(), request.getLanguage());
            flAddress = flService.getRegAddressAbout(flRecord.getIin(), request.getLanguage());
        } catch (Exception e) {
            log.warn("Could not fetch FL data for document {}: {}", request.getNumber(), e.getMessage());
        }

        //TODO перевод типа документа в зависимости от языка запроса
        String iinOrPassportStr;
        if (flRecord != null && flRecord.getDocuments() != null && !flRecord.getDocuments().isEmpty()) {
            FLDocument doc = flRecord.getDocuments().get(0);

            String docType = localizationHelper.toTitleCase(nullToEmpty(doc.getDocumentType()));
            String docNumber = doc.getDocumentNumber() != null ? "№" + doc.getDocumentNumber() : "";
            String beginDate = doc.getBeginDate() != null ? "от " + formatDate(doc.getBeginDate()) : "";
            String issueOrg = doc.getIssueOrg() != null
                    ? "выдано " + localizationHelper.toTitleCase(doc.getIssueOrg())
                    : "";
            String iin = flRecord.getIin() != null ? "ИИН " + flRecord.getIin() : "";

            iinOrPassportStr = String.join(" ",
                    docType, docNumber, beginDate, issueOrg, iin
            ).trim();
        } else {
            iinOrPassportStr = request.getNumber();
        }
        CaseInterrogationProtocol protocol = CaseInterrogationProtocol.builder()
                .fio(flRecord != null ? flRecord.getFio() : request.getFio())
                .sexId(flRecord != null ? flRecord.getSexId() : null)
                .dateOfBirth(flRecord != null && flRecord.getBirthDate() != null
                        ? flRecord.getBirthDate().toString() : null)
                .birthPlace(flRecord != null ? flRecord.getBirthRegion() : null)
                .citizenship(flRecord != null ? flRecord.getCitizenship() : null)
                .nationality(flRecord != null ? flRecord.getNationality() : null)
                .address(flAddress != null ? flAddress.getAddress() : null)
                .iinOrPassport(iinOrPassportStr)
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
                .language(request.getLanguage())
                .isDop(isDop)
                .protocol(protocol)
                .status(CaseInterrogationStatusEnum.IN_PROGRESS)
                .startedAt(now)
                .timerSessions(new ArrayList<>(List.of(session)))
                .build();

        session.setInterrogation(interrogation);

        String article = fetchArticleFromCaseInfo(caseNumber);
        if (article != null) {
            interrogation.setState(article);
        }
        caseEntity.getInterrogations().add(interrogation);
        Case savedCase = caseRepository.save(caseEntity);

        CaseInterrogation saved = savedCase.getInterrogations().stream()
                .filter(i -> request.getFio().equals(i.getFio()) && now.equals(i.getStartedAt()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve saved interrogation"));
        log.info("Interrogation added to case: {}", caseId);


        logService.log(
                String.format("Interrogation %s to %s added by user %s", saved.getFio(), caseNumber, email),
                LogLevel.INFO,
                LogAction.INTERROGATION_ADDED,
                caseNumber,
                user.getEmail()
        );
        return mapper.mapToInterrogationFullResponse(saved, user);
    }
    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "г.";
    }

    private String fetchArticleFromCaseInfo(String caseNumber) {
        try {
            String url = RequestUrlBuilder.caseInfoUrl(pythonHost, treePort, caseNumber);
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
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));


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
                            .orElseThrow(() -> new RuntimeException("Образование не найдено: " + eduId));
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
                            .orElseThrow(() -> new RuntimeException("Воинский учет не найден: " + militaryId));
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
                            .orElseThrow(() -> new RuntimeException("Судимость не найдена: " + criminalId));
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
                            .orElseThrow(() -> new RuntimeException("Отношение не найдено: " + relationId));
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
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

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
                            .orElseThrow(() -> new RuntimeException("Вовлеченные люди не найдены: " + involvedPersonsId));
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

    @Transactional
    public QAResponse uploadAudioAndEnqueue(Long caseId, Long interrogationId, Long qaId,
                                            String question, MultipartFile file, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        String audioUrl = minioService.uploadAudio(file, caseEntity.getNumber(), interrogation.getFio());

        CaseInterrogationQA qa;

        if (qaId != null) {
            qa = interrogation.getQaList().stream()
                    .filter(q -> q.getId().equals(qaId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Вопрос/ответ не найден: " + qaId));
            qa.setStatus(QAStatusEnum.TRANSCRIBING);
        } else {
            int orderIndex = interrogation.getQaList().size();
            qa = CaseInterrogationQA.builder()
                    .question(question)
                    .answer(null)
                    .status(QAStatusEnum.TRANSCRIBING)
                    .orderIndex(orderIndex)
                    .isEdited(false)
                    .createdAt(LocalDateTime.now())
                    .interrogation(interrogation)
                    .audioRecords(new ArrayList<>())
                    .build();
            interrogation.getQaList().add(qa);
        }

        CaseInterrogationAudioRecord record = CaseInterrogationAudioRecord.builder()
                .audioFileUrl(audioUrl)
                .transcribedText(null)
                .status(QAStatusEnum.TRANSCRIBING)
                .createdAt(LocalDateTime.now())
                .qa(qa)
                .build();
        qa.getAudioRecords().add(record);

        CaseInterrogation savedInterrogation = caseInterrogationRepository.save(interrogation);

        CaseInterrogationQA savedQa = savedInterrogation.getQaList().stream()
                .filter(q -> q.getAudioRecords().stream()
                        .anyMatch(r -> r.getAudioFileUrl().equals(audioUrl)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("QA не найден после сохранения"));


        Long recordId = savedQa.getAudioRecords().stream()
                .filter(r -> r.getAudioFileUrl().equals(audioUrl))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("AudioRecord не найден после сохранения"))
                .getId();

        audioQueueService.sendAudioForProcessing(AudioProcessingMessage.builder()
                .interrogationId(interrogationId)
                .qaId(savedQa.getId())
                .recordId(recordId)
                .caseNumber(caseEntity.getNumber())
                .audioFileUrl(audioUrl)
                .originalFileName(file.getOriginalFilename())
                .language(interrogation.getLanguage())
                .email(email)
                .fieldName(null)
                .build());

        logService.log(
                String.format("Uploading qa audio %s by %s user in case %s", audioUrl, email, caseEntity.getNumber()),
                LogLevel.INFO,
                LogAction.AUDIO_UPLOADED,
                caseEntity.getNumber(),
                email
        );

        return mapper.mapToQAResponse(savedQa);
    }


    @Override
    @Transactional
    public OtherAudioResponse uploadOtherAudioAndEnqueue(Long caseId, Long interrogationId, Long otherAudioId,
                                                         String fieldName, MultipartFile file,
                                                         String language, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        String audioUrl = minioService.uploadAudio(file, caseEntity.getNumber(), interrogation.getFio());

        CaseInterrogationOtherAudio otherAudio;

        if (otherAudioId != null) {
            otherAudio = interrogation.getOtherAudios().stream()
                    .filter(o -> o.getId().equals(otherAudioId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Айдио не найдено: " + otherAudioId));
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

        CaseInterrogation savedInterrogation = caseInterrogationRepository.save(interrogation);

        CaseInterrogationOtherAudio savedOther = savedInterrogation.getOtherAudios().stream()
                .filter(o -> o.getAudioRecords().stream()
                        .anyMatch(r -> r.getAudioFileUrl().equals(audioUrl)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("OtherAudio не найден после сохранения"));


        Long recordId = savedOther.getAudioRecords().stream()
                .filter(r -> r.getAudioFileUrl().equals(audioUrl))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("AudioRecord не найден после сохранения"))
                .getId();

        audioQueueService.sendAudioForProcessing(AudioProcessingMessage.builder()
                .interrogationId(interrogationId)
                .qaId(savedOther.getId())
                .recordId(recordId)
                .caseNumber(caseEntity.getNumber())
                .audioFileUrl(audioUrl)
                .originalFileName(file.getOriginalFilename())
                .language(language)
                .email(email)
                .fieldName(fieldName)
                .build());

        logService.log(
                String.format("Uploading additional audio %s by %s user in case %s", audioUrl, email, caseEntity.getNumber()),
                LogLevel.INFO,
                LogAction.AUDIO_UPLOADED,
                caseEntity.getNumber(),
                email
        );

        return mapper.mapToOtherAudioResponse(savedOther);
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
        qa.getAudioRecords().forEach(r -> r.setTranscribedText(null));

        qa.setAnswer(request.getAnswer());
        qa.setManuallyEdited(true);

        boolean assistantReplied = caseInterrogationChatRepository.findByInterrogationId(interrogationId)
                .map(chat -> chat.getMessages().stream()
                        .anyMatch(m -> m.getRole() == MessageRole.ASSISTANT && m.isComplete()))
                .orElse(false);

        qa.setIsEdited(assistantReplied);

        if (assistantReplied) {
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
        otherAudio.getAudioRecords().forEach(r -> r.setTranscribedText(null));

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
    public void completeInterrogation(long caseId, long interrogationId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(interrogation.getCaseEntity(), user);

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Допрос не принадлежит делу: " + caseId);
        }

        String caseNumber = interrogation.getCaseEntity().getNumber();
        LocalDateTime now = LocalDateTime.now();

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
                            ? 0 : interrogation.getAccumulatedSeconds();
                    interrogation.setAccumulatedSeconds(accumulated + sessionSeconds);
                    interrogation.setDurationSeconds(interrogation.getAccumulatedSeconds());
                }
            }

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
            if (interrogation.getStartedAt() == null) {
                interrogation.setStartedAt(now);
            }

            interrogation.setIsPaused(false);

            CaseInterrogationTimerSession session = CaseInterrogationTimerSession.builder()
                    .interrogation(interrogation)
                    .startedAt(now)
                    .build();
            interrogation.getTimerSessions().add(session);

        } else if ("pause".equals(action)) {
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

    @Transactional
    public List<CaseInterrogationApplicationFileResponse> uploadApplicationFiles(
            Long caseId, Long interrogationId, List<MultipartFile> files,
            Map<String, String> displayNames, String email) {

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
                        file, caseEntity.getNumber(), interrogation.getFio()
                );

                String displayName = displayNames.getOrDefault(originalName, originalName);
                appFile.setDisplayName(displayName);

                try {
                    Integer pages = pageCounter.countPagesByUrl(appFile.getFileUrl(), appFile.getContentType());
                    if (pages != null) appFile.setPages(pages);
                } catch (Exception e) {
                    log.warn("Could not count pages for {}: {}", appFile.getOriginalFileName(), e.getMessage());
                }

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
                log.error("Failed to upload application file: {} for interrogation: {}", originalName, interrogationId, e);
            }
        }
        uploadedCaseFiles.forEach(caseFile -> {
            try {
                Integer pages = pageCounter.countPagesByUrl(caseFile.getFileUrl(), caseFile.getContentType());
                if (pages != null) caseFile.setPages(pages);
            } catch (Exception e) {
                log.warn("Could not count pages for file {}: {}", caseFile.getOriginalFileName(), e.getMessage());
            }
        });

        caseInterrogationRepository.saveAndFlush(interrogation);
        uploadedFiles = new ArrayList<>(interrogation.getApplicationFiles());

        for (CaseFile caseFile : uploadedCaseFiles) {
            taskQueueService.addTaskToQueue(
                    email, caseEntity.getId(), caseEntity.getNumber(),
                    caseFile.getOriginalFileName(), caseFile.getFileUrl(), caseFile.getId()
            );
        }

        String fileNames = uploadedFiles.stream()
                .map(CaseInterrogationApplicationFile::getOriginalFileName)
                .collect(Collectors.joining(", "));

        logService.log(
                String.format("Uploading application files [%s] to interrogation №%d (FIO: %s) by user %s in case №%s",
                        fileNames, interrogationId, interrogation.getFio(), email, caseNumber),
                LogLevel.INFO, LogAction.FILE_UPLOAD, caseNumber, email
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
