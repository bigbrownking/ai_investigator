package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.*;
import org.di.digital.model.*;
import org.di.digital.service.MinioService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Mapper {
    private final MinioService minioService;
    public CaseInterrogationResponse mapToInterrogationResponse(CaseInterrogation interrogation) {
        return CaseInterrogationResponse.builder()
                .id(interrogation.getId())
                .number(interrogation.getNumber())
                .documentType(interrogation.getDocumentType())
                .fio(interrogation.getFio())
                .role(interrogation.getRole())
                .date(String.valueOf(interrogation.getDate()))
                .status(interrogation.getStatus().name())
                .isDop(interrogation.getIsDop())
                .build();
    }
    public CaseInterrogationQAResponse mapToInterrogationQAResponse(CaseInterrogationQA caseInterrogationQA){
        return CaseInterrogationQAResponse.builder()
                .id(caseInterrogationQA.getId())
                .interrogationId(caseInterrogationQA.getInterrogation().getId())
                .question(caseInterrogationQA.getQuestion())
                .answer(caseInterrogationQA.getAnswer())
                .status(caseInterrogationQA.getStatus().name())
                .createAt(caseInterrogationQA.getCreatedAt())
                .build();
    }

    public CaseInterrogationProtocolResponse mapToInterrogationProtocolResponse(CaseInterrogationProtocol protocol) {
        List<EducationResponse> educations = protocol.getEducations() != null
                ? protocol.getEducations().stream()
                .map(e -> EducationResponse.builder()
                        .id(e.getId())
                        .type(e.getType())
                        .edu(e.getEdu())
                        .build())
                .toList()
                : List.of();
        return CaseInterrogationProtocolResponse.builder()
                .fio(protocol.getFio())
                .dateOfBirth(protocol.getDateOfBirth())
                .birthPlace(protocol.getBirthPlace())
                .citizenship(protocol.getCitizenship())
                .nationality(protocol.getNationality())
                .educations(educations)
                .martialStatus(protocol.getMartialStatus())
                .workOrStudyPlace(protocol.getWorkOrStudyPlace())
                .position(protocol.getPosition())
                .address(protocol.getAddress())
                .contactPhone(protocol.getContactPhone())
                .contactEmail(protocol.getContactEmail())
                .other(protocol.getOther())
                .relation(protocol.getRelation())
                .technical(protocol.getTechnical())
                .military(protocol.getMilitary())
                .criminalRecord(protocol.getCriminalRecord())
                .iinOrPassport(protocol.getIinOrPassport())
                .interrogationId(protocol.getInterrogation() != null ? protocol.getInterrogation().getId() : null)
                .build();
    }

    public CaseResponse mapToCaseResponse(Case caseEntity) {
        return CaseResponse.builder()
                .id(caseEntity.getId())
                .title(caseEntity.getTitle())
                .number(caseEntity.getNumber())
                .status(caseEntity.isStatus())
                .files(caseEntity.getFiles().stream()
                        .map(this::mapToCaseFileResponse)
                        .collect(Collectors.toList()))
                .interrogations(caseEntity.getInterrogations().stream()
                        .map(this::mapToInterrogationResponse)
                        .collect(Collectors.toList()))
                .users(caseEntity.getUsers().stream()
                        .map(user -> mapToCaseUserResponse(user, caseEntity))
                        .collect(Collectors.toList()))
                .createdDate(caseEntity.getCreatedDate())
                .ownerEmail(caseEntity.getOwner() != null ? caseEntity.getOwner().getEmail() : null)
                .lastActivityDate(caseEntity.getLastActivityDate())
                .lastActivityType(caseEntity.getLastActivityType())
                .qualificationGeneratedAt(caseEntity.getQualificationGeneratedAt())
                .indictmentGeneratedAt(caseEntity.getIndictmentGeneratedAt())
                .updatedDate(caseEntity.getUpdatedDate())
                .build();
    }
    public CaseUserResponse mapToCaseUserResponse(User user, Case caseEntity) {
        return CaseUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .surname(user.getSurname())
                .fathername(user.getFathername())
                .isOwner(caseEntity.isOwner(user))
                .build();
    }

    public UserProfile mapToUserProfileResponse(User user) {
        String roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .reduce((r1, r2) -> r1 + ", " + r2)
                .orElse("");

        UserSettingsDto settingsDto = null;
        if (user.getSettings() != null) {
            settingsDto = UserSettingsDto.builder()
                    .level(user.getSettings().getLevel() != null ? user.getSettings().getLevel().name() : null)
                    .language(user.getSettings().getLanguage().getLanguage())
                    .theme(user.getSettings().getTheme().getTheme())
                    .build();
        }

        return UserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .surname(user.getSurname())
                .fathername(user.getFathername())
                .role(roles)
                .profession(user.getProfession())
                .region(user.getRegion())
                .email(user.getEmail())
                .active(user.isActive())
                .settings(settingsDto)
                .street(user.getStreet())
                .createdCaseCount(user.getCases() != null ? user.getCases().size() : 0)
                .build();
    }

    public FigurantResponse mapToFigurantResponse(Figurant figurant) {
        return FigurantResponse.builder()
                .id(figurant.getId())
                .documentType(figurant.getDocumentType())
                .number(figurant.getNumber())
                .fio(figurant.getFio())
                .role(figurant.getRole())
                .build();
    }

    public CaseInterrogationFullResponse mapToInterrogationFullResponse(CaseInterrogation interrogation, User user) {
        String profession = interrogation.getInvestigatorProfession() != null
                ? interrogation.getInvestigatorProfession()
                : user.getProfession();

        String region = interrogation.getInvestigatorRegion() != null
                ? interrogation.getInvestigatorRegion()
                : user.getRegion();

        String street = interrogation.getAddrezz() != null
                ? interrogation.getAddrezz()
                : user.getStreet();
        String city = interrogation.getCity() != null
                ? interrogation.getCity()
                : "г. Астана";

        CaseInterrogationProtocolResponse protocolResponse = null;
        if (interrogation.getProtocol() != null) {
            protocolResponse = mapToInterrogationProtocolResponse(interrogation.getProtocol());
        }

        List<CaseInterrogationQAResponse> qaList = null;
        if (interrogation.getQaList() != null && !interrogation.getQaList().isEmpty()) {
            qaList = interrogation.getQaList().stream()
                    .map(this::mapToInterrogationQAResponse)
                    .toList();
        }

        List<InterrogationTimerSessionResponse> timerSessions = null;
        if (interrogation.getTimerSessions() != null) {
            timerSessions = interrogation.getTimerSessions().stream()
                    .map(s -> InterrogationTimerSessionResponse.builder()
                            .startedAt(s.getStartedAt())
                            .pausedAt(s.getPausedAt())
                            .build())
                    .toList();
        }

        List<CaseInterrogationApplicationFileResponse> applicationFiles = null;
        if (interrogation.getApplicationFiles() != null && !interrogation.getApplicationFiles().isEmpty()) {
            applicationFiles = interrogation.getApplicationFiles().stream()
                    .map(this::mapToApplicationFileResponse)
                    .toList();
        }
        String caseNumber = interrogation.getCaseEntity().getNumber();
        return CaseInterrogationFullResponse.builder()
                .id(interrogation.getId())
                .room(interrogation.getRoom())
                .city(city)
                .personYear(interrogation.getPersonYear())
                .personSpecialist(interrogation.getPersonSpecialist())
                .personTranslator(interrogation.getPersonTranslator())
                .addrezz(street)
                .notificationNumber(interrogation.getNotificationNumber())
                .notificationDate(interrogation.getNotificationDate())
                .state(interrogation.getState())
                .caseNumberState(interrogation.getCaseNumberState())
                .caseNumber(caseNumber)
                .number(interrogation.getNumber())
                .documentType(interrogation.getDocumentType())
                .fio(interrogation.getFio())
                .role(interrogation.getRole())
                .date(interrogation.getDate())
                .involved(interrogation.getInvolved())
                .involvedPersons(interrogation.getInvolvedPersons())
                .confession(interrogation.getConfession())
                .confessionText(interrogation.getConfessionText())
                .language(interrogation.getLanguage())
                .translator(interrogation.getTranslator())
                .defender(interrogation.getDefender())
                .familiarization(interrogation.getFamiliarization())
                .additionalInfo(interrogation.getAdditionalInfo())
                .additionalText(interrogation.getAdditionalText())
                .application(interrogation.getApplication())
                .investigator(interrogation.getInvestigator())
                .investigatorProfession(profession)
                .investigatorRegion(region)
                .status(interrogation.getStatus().name())
                .protocol(protocolResponse)
                .startedAt(interrogation.getStartedAt())
                .finishedAt(interrogation.getFinishedAt())
                .durationSeconds(interrogation.getDurationSeconds())
                .timerSessions(timerSessions)
                .qaList(qaList)
                .applications(applicationFiles)
                .isDop(interrogation.getIsDop())
                .build();
    }

    public CaseFileResponse mapToCaseFileResponse(CaseFile f) {
        return CaseFileResponse.builder()
                .id(f.getId())
                .originalFileName(f.getOriginalFileName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .status(f.getStatus().getLabel())
                .previewUrl(minioService.generatePresignedUrlForPreview(f.getFileUrl()))
                .downloadUrl(minioService.generatePresignedUrlForDownload(f.getFileUrl(), f.getOriginalFileName()))
                .uploadedAt(String.valueOf(f.getUploadedAt()))
                .isQualification(f.isQualification())
                .isPlan(f.isPlan())
                .isPlanComponent(f.isPlanComponent())
                .tom(f.getTom())
                .build();
    }

    public CaseInterrogationApplicationFileResponse mapToApplicationFileResponse(CaseInterrogationApplicationFile file) {
        return CaseInterrogationApplicationFileResponse.builder()
                .id(file.getId())
                .originalFileName(file.getOriginalFileName())
                .storedFileName(file.getStoredFileName())
                .previewUrl(minioService.generatePresignedUrlForPreview(file.getFileUrl()))
                .downloadUrl(minioService.generatePresignedUrlForDownload(file.getFileUrl(), file.getOriginalFileName()))
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .uploadedAt(String.valueOf(file.getUploadedAt()))
                .build();
    }

    public QAResponse mapToQAResponse(CaseInterrogationQA caseInterrogationQA){
        return QAResponse.builder()
                .id(caseInterrogationQA.getId())
                .question(caseInterrogationQA.getQuestion())
                .answer(caseInterrogationQA.getAnswer())
                .orderIndex(caseInterrogationQA.getOrderIndex())
                .edited(caseInterrogationQA.getIsEdited())
                .status(caseInterrogationQA.getStatus())
                .build();
    }
}
