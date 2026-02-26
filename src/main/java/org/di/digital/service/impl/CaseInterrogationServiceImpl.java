package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.AudioProcessingMessage;
import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.UpdateProtocolFieldRequest;
import org.di.digital.dto.response.*;
import org.di.digital.model.*;
import org.di.digital.model.enums.CaseInterrogationStatusEnum;
import org.di.digital.model.enums.QAStatusEnum;
import org.di.digital.repository.CaseInterrogationRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.CaseInterrogationService;
import org.di.digital.service.impl.queue.AudioQueueService;
import org.di.digital.util.Mapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseInterrogationServiceImpl implements CaseInterrogationService {
    private final CaseRepository caseRepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final AudioQueueService audioQueueService;
    private final Mapper mapper;

    @Transactional(readOnly = true)
    public List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, LocalDate date, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        return caseEntity.getInterrogations().stream()
                .filter(i -> role.equals("Все") || i.getRole().equalsIgnoreCase(role))
                .filter(i -> fio == null || i.getFio().toLowerCase().contains(fio.toLowerCase()))
                .filter(i -> date == null || i.getDate().equals(date))
                .map(mapper::mapToInterrogationResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CaseResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        LocalDateTime now = LocalDateTime.now();

        InterrogationTimerSession session = InterrogationTimerSession.builder()
                .startedAt(now)
                .build();

        CaseInterrogation interrogation = CaseInterrogation.builder()
                .iin(request.getIin())
                .fio(request.getFio())
                .role(request.getRole())
                .date(LocalDate.now())
                .caseEntity(caseEntity)
                .status(CaseInterrogationStatusEnum.IN_PROGRESS)
                .startedAt(now)
                .timerSessions(new ArrayList<>(List.of(session)))
                .build();

        session.setInterrogation(interrogation);

        caseEntity.getInterrogations().add(interrogation);
        caseRepository.save(caseEntity);

        log.info("Interrogation added to case: {}", caseId);
        return mapper.mapToCaseResponse(caseEntity);
    }

    @Override
    public CaseResponse deleteInterrogation(Long caseId, Long interrogationId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found with id: " + interrogationId));
        caseEntity.removeInterrogation(interrogation);
        caseRepository.save(caseEntity);
        log.info("Interrogation removed from case: {}", caseId);

        return mapper.mapToCaseResponse(caseEntity);
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
            case "education"      -> protocol.setEducation(request.getValue());
            case "martialStatus"  -> protocol.setMartialStatus(request.getValue());
            case "workOrStudyPlace"-> protocol.setWorkOrStudyPlace(request.getValue());
            case "position"       -> protocol.setPosition(request.getValue());
            case "address"        -> protocol.setAddress(request.getValue());
            case "contacts"       -> protocol.setContacts(request.getValue());
            case "military"       -> protocol.setMilitary(request.getValue());
            case "criminalRecord" -> protocol.setCriminalRecord(request.getValue());
            case "iinOrPassport"  -> protocol.setIinOrPassport(request.getValue());
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

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

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
                .build());

        return QAResponse.builder()
                .question(question)
                .answer(null)
                .orderIndex(orderIndex)
                .status(QAStatusEnum.TRANSCRIBING)
                .build();
    }

    @Transactional(readOnly = true)
    public List<QAResponse> getQAList(Long caseId, Long interrogationId, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        CaseInterrogation interrogation = caseEntity.getInterrogations().stream()
                .filter(i -> i.getId().equals(interrogationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        return interrogation.getQaList().stream()
                .map(qa -> QAResponse.builder()
                        .question(qa.getQuestion())
                        .answer(qa.getAnswer())
                        .orderIndex(qa.getOrderIndex())
                        .status(qa.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CaseInterrogationFullResponse getDetailed(long caseId, long interrogationId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!interrogation.getCaseEntity().isOwner(user) && !interrogation.getCaseEntity().hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Interrogation does not belong to case: " + caseId);
        }

        CaseInterrogationProtocolResponse protocolResponse = null;
        if (interrogation.getProtocol() != null) {
            CaseInterrogationProtocol p = interrogation.getProtocol();
            protocolResponse = mapper.mapToInterrogationProtocolResponse(p);
        }

        List<CaseInterrogationQAResponse> qaList = null;
        if(!interrogation.getQaList().isEmpty()){
            List<CaseInterrogationQA> qas = interrogation.getQaList();
            qaList = qas.stream()
                    .map(mapper::mapToInterrogationQAResponse).toList();
        }

        List<InterrogationTimerSessionResponse> timerSessions = interrogation.getTimerSessions().stream()
                .map(s -> InterrogationTimerSessionResponse.builder()
                        .startedAt(s.getStartedAt())
                        .pausedAt(s.getPausedAt())
                        .build())
                .toList();

        return CaseInterrogationFullResponse.builder()
                .id(interrogation.getId())
                .caseNumber(interrogation.getCaseEntity().getNumber())
                .iin(interrogation.getIin())
                .fio(interrogation.getFio())
                .role(interrogation.getRole())
                .date(interrogation.getDate())
                .status(interrogation.getStatus().name())
                .protocol(protocolResponse)
                .startedAt(interrogation.getStartedAt())
                .finishedAt(interrogation.getFinishedAt())
                .durationSeconds(interrogation.getDurationSeconds())
                .timerSessions(timerSessions)
                .qaList(qaList)
                .build();
    }

    @Override
    public void completeInterrogation(long caseId, long interrogationId, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!interrogation.getCaseEntity().isOwner(user) && !interrogation.getCaseEntity().hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Interrogation does not belong to case: " + caseId);
        }
        if (interrogation.getStartedAt() != null && interrogation.getFinishedAt() == null) {
            LocalDateTime now = LocalDateTime.now();
            interrogation.setFinishedAt(now);
            long seconds = ChronoUnit.SECONDS.between(interrogation.getStartedAt(), now);
            interrogation.setDurationSeconds(seconds);
        }

        interrogation.setStatus(CaseInterrogationStatusEnum.COMPLETED);
        caseInterrogationRepository.save(interrogation);
    }

    @Transactional
    public void controlTimer(Long caseId, Long interrogationId, String action, String email) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Interrogation not found: " + interrogationId));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (!interrogation.getCaseEntity().isOwner(user) && !interrogation.getCaseEntity().hasUser(user)) {
            throw new AccessDeniedException("Access denied to case: " + caseId);
        }

        if (!interrogation.getCaseEntity().getId().equals(caseId)) {
            throw new RuntimeException("Interrogation does not belong to case: " + caseId);
        }

        if ("start".equals(action)) {
            if (interrogation.getStartedAt() != null && interrogation.getFinishedAt() == null) {
                throw new IllegalStateException("Timer is already running");
            }

            LocalDateTime now = LocalDateTime.now();

            if (interrogation.getStartedAt() == null) {
                interrogation.setStartedAt(now);
            }

            interrogation.setFinishedAt(null);
            InterrogationTimerSession session = InterrogationTimerSession.builder()
                    .interrogation(interrogation)
                    .startedAt(now)
                    .build();
            interrogation.getTimerSessions().add(session);

        } else if ("pause".equals(action)) {
            if (interrogation.getStartedAt() == null || interrogation.getFinishedAt() != null) {
                throw new IllegalStateException("Timer is not running");
            }
            LocalDateTime now = LocalDateTime.now();
            interrogation.setFinishedAt(now);

            long seconds = ChronoUnit.SECONDS.between(
                    interrogation.getTimerSessions().get(interrogation.getTimerSessions().size() - 1).getStartedAt(),
                    now
            );
            long accumulated = interrogation.getAccumulatedSeconds() == null ? 0 : interrogation.getAccumulatedSeconds();
            interrogation.setAccumulatedSeconds(accumulated + seconds);
            interrogation.setDurationSeconds(interrogation.getAccumulatedSeconds());

            InterrogationTimerSession lastSession = interrogation.getTimerSessions()
                    .get(interrogation.getTimerSessions().size() - 1);
            lastSession.setPausedAt(now);
        }

        caseInterrogationRepository.save(interrogation);
    }
}
