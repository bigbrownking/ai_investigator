package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.interrogation.AddInterrogationRequest;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.*;
import org.di.digital.model.fl.FLAddress;
import org.di.digital.model.fl.FLDocument;
import org.di.digital.model.fl.FLRecord;
import org.di.digital.model.interrogation.*;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.util.LocalizationHelper;
import org.di.digital.util.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterrogationCreateWriter {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final LogService logService;
    private final Mapper mapper;
    private final LocalizationHelper localizationHelper;
    private final InterrogationCategoryResolver categoryResolver;
    private final CaseInterrogationRepository caseInterrogationRepository;

    private static final Duration MIN_DOP_INTERVAL = Duration.ofMinutes(2);

    @Transactional(readOnly = true)
    public String getCaseNumber(Long caseId) {
        return caseRepository.findById(caseId)
                .map(Case::getNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
    }

    @Transactional
    public CaseInterrogationFullResponse createInterrogation(
            Long caseId, String email, AddInterrogationRequest request,
            LocalDateTime now, FLRecord flRecord, FLAddress flAddress, String article) {

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
        String lang = request.getLanguage();

        InterrogationSpecialGround ground = categoryResolver.resolveFromFl(flRecord);

        String iinOrPassportStr;
        if (flRecord != null && flRecord.getDocuments() != null && !flRecord.getDocuments().isEmpty()) {
            FLDocument doc = flRecord.getDocuments().get(0);
            String docType = localizationHelper.localizeDocumentType(doc.getDocumentType(), lang);
            String docNumber = doc.getDocumentNumber() != null ? "№" + doc.getDocumentNumber() : "";
            String beginDate = doc.getBeginDate() != null
                    ? localizationHelper.localizeFromWord(lang) + " " + formatDate(doc.getBeginDate())
                    : "";
            String issueOrg = doc.getIssueOrg() != null
                    ? localizationHelper.localizeIssuedWord(lang) + " " + localizationHelper.toTitleCase(doc.getIssueOrg())
                    : "";
            String iin = flRecord.getIin() != null
                    ? localizationHelper.localizeIinLabel(lang) + " " + flRecord.getIin()
                    : "";
            iinOrPassportStr = String.join(" ", docType, docNumber, beginDate, issueOrg, iin)
                    .trim().replaceAll("\\s+", " ");
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

        Optional<CaseInterrogation> previous = caseEntity.getInterrogations().stream()
                .filter(i -> request.getNumber().equals(i.getNumber())
                        && request.getRole().equals(i.getRole()))
                .max(Comparator.comparing(CaseInterrogation::getStartedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));

        boolean isDop = previous.isPresent();
        if (isDop) {
            LocalDateTime finishedAt = previous.get().getFinishedAt();
            if (finishedAt == null) {
                throw new IllegalStateException(
                        "Предыдущий допрос ещё не завершён — дополнительный допрос недоступен");
            }
            Duration elapsed = Duration.between(finishedAt, now);
            if (elapsed.compareTo(MIN_DOP_INTERVAL) < 0) {
                long minutesLeft = MIN_DOP_INTERVAL.minus(elapsed).toMinutes();
                throw new IllegalStateException(
                        "Дополнительный допрос можно начать не ранее чем через 2 часа после окончания предыдущего. Осталось: "
                                + minutesLeft + " мин.");
            }
        }

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
                .isPaused(true)
                .timerSessions(new ArrayList<>())
                .specialGround(ground != null ? ground : InterrogationSpecialGround.NONE)
                .limitProfile(ground != null && ground.isSpecial()
                        ? InterrogationLimitProfile.SPECIAL : InterrogationLimitProfile.STANDARD)
                .categoryConfirmed(ground != null)
                .build();

        if (article != null) {
            interrogation.setState(article);
        }

        caseEntity.getInterrogations().add(interrogation);
        CaseInterrogation saved = caseInterrogationRepository.saveAndFlush(interrogation);

        log.info("Interrogation added to case: {}", caseId);

        logService.log(
                String.format("Interrogation %s to %s added by user %s",
                        interrogation.getFio(), caseNumber, email),
                LogLevel.INFO, LogAction.INTERROGATION_ADDED, caseNumber, user.getEmail());

        return mapper.mapToInterrogationFullResponse(saved, user);
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "г.";
    }
}