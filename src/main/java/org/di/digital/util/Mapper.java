package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.*;
import org.di.digital.model.*;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.di.digital.service.MinioService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.di.digital.util.UserUtil.getCurrentUser;

@Component
@RequiredArgsConstructor
public class Mapper {
    private final MinioService minioService;
    private final LocalizationHelper localizationHelper;

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
                        .about(e.getAbout())
                        .build())
                .toList()
                : List.of();

        List<CriminalResponse> criminals = protocol.getCriminals() != null
                ? protocol.getCriminals().stream()
                .map(e -> CriminalResponse.builder()
                        .id(e.getId())
                        .type(e.getType())
                        .about(e.getAbout())
                        .build())
                .toList()
                : List.of();

        List<MilitaryResponse> militaries = protocol.getMilitaries() != null
                ? protocol.getMilitaries().stream()
                .map(e -> MilitaryResponse.builder()
                        .id(e.getId())
                        .type(e.getType())
                        .about(e.getAbout())
                        .build())
                .toList()
                : List.of();

        List<RelationResponse> relations = protocol.getRelationRecords() != null
                ? protocol.getRelationRecords().stream()
                .map(e -> RelationResponse.builder()
                        .id(e.getId())
                        .type(e.getType())
                        .about(e.getAbout())
                        .build())
                .toList()
                : List.of();
        String genderCorrectedStatus = formatMartialStatus(protocol);
        return CaseInterrogationProtocolResponse.builder()
                .fio(localizationHelper.toTitleCase(protocol.getFio()))
                .dateOfBirth(localizationHelper.formatToRussianDate(protocol.getDateOfBirth()))
                .birthPlace(localizationHelper.toTitleCase(protocol.getBirthPlace()))
                .citizenship("гражданин Республики "+ localizationHelper.toTitleCase(protocol.getCitizenship()))
                .nationality(localizationHelper.toTitleCase(protocol.getNationality()))
                .educations(educations)
                .martialStatus(genderCorrectedStatus)
                .workOrStudyPlace(protocol.getWorkOrStudyPlace())
                .position(protocol.getPosition())
                .address(protocol.getAddress())
                .contactPhone(protocol.getContactPhone())
                .contactEmail(protocol.getContactEmail())
                .other(protocol.getOther())
                .relation(relations)
                .technical(protocol.getTechnical())
                .military(militaries)
                .criminalRecord(criminals)
                .iinOrPassport(protocol.getIinOrPassport())
                .interrogationId(protocol.getInterrogation() != null ? protocol.getInterrogation().getId() : null)
                .build();
    }
    private String formatMartialStatus(CaseInterrogationProtocol protocol) {
        String status = protocol.getMartialStatus();
        String sexId = protocol.getSexId();
        if (status == null) return null;

        String s = status.toLowerCase().trim();
        boolean isFemale = "2".equals(sexId);

        if (s.contains("холост") || s.contains("не замужем")) {
            return isFemale ? "не замужем" : "холост";
        }
        if (s.contains("женат") || s.contains("замужем")) {
            return isFemale ? "замужем" : "женат";
        }
        if (s.contains("разведен") || s.contains("разведена")) {
            return isFemale ? "разведена" : "разведен";
        }
        if (s.contains("вдовец") || s.contains("вдова")) {
            return isFemale ? "вдова" : "вдовец";
        }

        return status;
    }

    public CaseResponse mapToCaseResponse(Case caseEntity) {
        return CaseResponse.builder()
                .id(caseEntity.getId())
                .title(caseEntity.getTitle())
                .number(caseEntity.getNumber())
                .status(caseEntity.isStatus())
                .files(caseEntity.getFiles().stream()
                        .sorted(Comparator
                                .comparing(CaseFile::getTom, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(f -> extractLeadingNumber(f.getOriginalFileName()), Comparator.nullsLast(Comparator.naturalOrder()))                                .thenComparing(CaseFile::getOriginalFileName, Comparator.nullsLast(Comparator.naturalOrder())))
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

    private Long extractLeadingNumber(String fileName) {
        if (fileName == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)").matcher(fileName);
        return m.find() ? Long.parseLong(m.group(1)) : null;
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

        UserSettingsLanguage language = user.getSettings().getLanguage();
        UserSettingsDto settingsDto = null;
        if (user.getSettings() != null) {
            settingsDto = UserSettingsDto.builder()
                    .level(user.getSettings().getLevel() != null ? user.getSettings().getLevel().name() : null)
                    .language(language.getLanguage())
                    .theme(user.getSettings().getTheme().getTheme())
                    .build();
        }

        return UserProfile.builder()
                .id(user.getId())
                .iin(user.getIin())
                .name(user.getName())
                .surname(user.getSurname())
                .fathername(user.getFathername())
                .role(roles)
                .administration(localizationHelper.getLocalizedName(user.getAdministration(), language))
                .profession(localizationHelper.getLocalizedName(user.getProfession(), language))
                .region(localizationHelper.getLocalizedName(user.getRegion(), language))
                .email(user.getEmail())
                .active(user.isActive())
                .settings(settingsDto)
                .street(localizationHelper.getLocalizedName(user.getRegion().getAddresses().get(0), language))
                .createdCaseCount(user.getCases() != null ? user.getCases().size() : 0)
                .build();
    }

    public FigurantResponse mapToFigurantResponse(CaseFigurant figurant) {
        return FigurantResponse.builder()
                .id(figurant.getId())
                .documentType(figurant.getDocumentType())
                .number(figurant.getNumber())
                .fio(figurant.getFio())
                .role(figurant.getRole())
                .build();
    }

    public CaseInterrogationFullResponse mapToInterrogationFullResponse(CaseInterrogation interrogation, User user) {
        String userProf = localizationHelper.getLocalizedName(
                user.getProfession(), user.getSettings().getLanguage());
        String profession = interrogation.getInvestigatorProfession() != null
                ? interrogation.getInvestigatorProfession()
                : userProf;

        String region = localizationHelper.getGenitive(
                interrogation.getInvestigatorRegion() != null
                        ? interrogation.getInvestigatorRegion()
                        : user.getRegion().getRuName()
        );

        String administration = localizationHelper.getGenitive(
                interrogation.getInvestigatorAdministration() != null
                        ? interrogation.getInvestigatorAdministration()
                        : user.getAdministration().getRuName()
        );

        String userAdr = localizationHelper.getLocalizedName(
                user.getRegion().getAddresses().get(0), user.getSettings().getLanguage());
        String address = interrogation.getAddrezz() != null
                ? interrogation.getAddrezz()
                : userAdr;

        String city = interrogation.getCity() != null
                ? interrogation.getCity()
                : "г. Астана";

        CaseInterrogationProtocolResponse protocolResponse = null;
        String fio = interrogation.getFio();
        if (interrogation.getProtocol() != null) {
            protocolResponse = mapToInterrogationProtocolResponse(interrogation.getProtocol());
            fio = localizationHelper.toTitleCase(interrogation.getProtocol().getFio());
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
        String investigator = interrogation.getInvestigator() != null
                ? interrogation.getInvestigator()
                : user.getSurname() + " " + user.getName() + " " + user.getFathername();

        String caseNumber = interrogation.getCaseEntity().getNumber();
        return CaseInterrogationFullResponse.builder()
                .id(interrogation.getId())
                .room(interrogation.getRoom())
                .city(city)
                .personYear(interrogation.getPersonYear())
                .personSpecialist(interrogation.getPersonSpecialist())
                .personTranslator(interrogation.getPersonTranslator())
                .addrezz(address)
                .notificationNumber(interrogation.getNotificationNumber())
                .notificationDate(interrogation.getNotificationDate())
                .state(interrogation.getState())
                .caseNumberState(interrogation.getCaseNumberState())
                .caseNumber(caseNumber)
                .number(interrogation.getNumber())
                .documentType(interrogation.getDocumentType())
                .fio(fio)
                .role(interrogation.getRole())
                .date(interrogation.getDate())
                .involved(interrogation.getInvolved())
                .involvedPersons(interrogation.getInvolvedPersons())
                .testimony(interrogation.getTestimony())
                .confession(interrogation.getConfession())
                .confessionText(interrogation.getConfessionText())
                .language(interrogation.getLanguage() == null || interrogation.getLanguage().equals("русском") ? "русском" : "казахском")
                .translator(interrogation.getTranslator())
                .defender(interrogation.getDefender())
                .familiarization(interrogation.getFamiliarization())
                .additionalInfo(interrogation.getAdditionalInfo())
                .additionalText(interrogation.getAdditionalText())
                .application(interrogation.getApplication())
                .investigator(investigator)
                .investigatorProfession(profession)
                .investigatorAdministration(administration)
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
    public AppealDto toAppealDto(Appeal appeal) {
        return AppealDto.builder()
                .id(appeal.getId())
                .userId(appeal.getUser().getId())
                .userName(appeal.getUser().getName())
                .userSurname(appeal.getUser().getSurname())
                .userFathername(appeal.getUser().getFathername())
                .userEmail(appeal.getUser().getEmail())
                .regionId(appeal.getRegion() != null ? appeal.getRegion().getId() : null)
                .regionName(localizationHelper.getLocalizedName(appeal.getRegion(), getCurrentUser().getSettings().getLanguage()))
                .status(appeal.getStatus().getDescription())
                .createdAt(appeal.getCreatedAt())
                .reviewedAt(appeal.getReviewedAt())
                .build();
    }
    public ProfessionDto toProfessionDto(Profession profession){
        return ProfessionDto.builder()
                .id(profession.getId())
                .name(profession.getRuName())
                .build();
    }

    public RegionDto toRegionDto(Region region){
        return RegionDto.builder()
                .id(region.getId())
                .name(region.getRuName())
                .build();
    }

    public AdministrationDto toAdministrationDto(Administration administration){
        return AdministrationDto.builder()
                .id(administration.getId())
                .name(administration.getRuName())
                .build();
    }
}
