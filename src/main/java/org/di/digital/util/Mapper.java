package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.cases.CaseFileResponse;
import org.di.digital.dto.response.cases.CaseListResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.cases.CaseUserResponse;
import org.di.digital.dto.response.interrogation.*;
import org.di.digital.dto.response.plan.ManagementPendingPlanDto;
import org.di.digital.dto.response.plan.PlanApprovalHistoryDto;
import org.di.digital.dto.response.plan.PlanEditHistoryDto;
import org.di.digital.dto.response.support.ReviewDto;
import org.di.digital.dto.response.support.SupportTicketDto;
import org.di.digital.dto.response.support.SupportTicketPhotoDto;
import org.di.digital.dto.response.user.*;
import org.di.digital.model.plan.PlanApprovalHistory;
import org.di.digital.model.plan.PlanEditHistory;
import org.di.digital.model.user.Administration;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.interrogation.CaseFigurant;
import org.di.digital.model.Log;
import org.di.digital.model.user.Profession;
import org.di.digital.model.user.Rank;
import org.di.digital.model.user.Region;
import org.di.digital.model.user.Role;
import org.di.digital.model.user.User;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.di.digital.model.interrogation.*;
import org.di.digital.model.support.Review;
import org.di.digital.model.support.SupportTicket;
import org.di.digital.model.user.*;
import org.di.digital.repository.user.UserFaceTemplateRepository;
import org.di.digital.service.MinioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@Component
@RequiredArgsConstructor
public class Mapper {
    private final MinioService minioService;
    private final LocalizationHelper localizationHelper;
    private final UserFaceTemplateRepository faceTemplateRepository;

    @Value("${last.seen.ttl}")
    private int ttl;

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
                .audioUsed(interrogation.isAudioUsed())
                .build();
    }

    public CaseInterrogationQAResponse mapToInterrogationQAResponse(CaseInterrogationQA caseInterrogationQA) {
        List<AudioRecordResponse> audioRecords = caseInterrogationQA.getAudioRecords().stream()
                .map(this::mapToAudioRecordResponse)
                .toList();

        return CaseInterrogationQAResponse.builder()
                .id(caseInterrogationQA.getId())
                .interrogationId(caseInterrogationQA.getInterrogation().getId())
                .question(caseInterrogationQA.getQuestion())
                .answer(caseInterrogationQA.getAnswer())
                .status(caseInterrogationQA.getStatus().name())
                .createAt(caseInterrogationQA.getCreatedAt())
                .audioRecords(audioRecords)
                .build();
    }

    public AudioRecordResponse mapToAudioRecordResponse(CaseInterrogationAudioRecord record) {
        return AudioRecordResponse.builder()
                .id(record.getId())
                .audioUrl(record.getAudioFileUrl() != null
                        ? minioService.generatePresignedUrlForPreview(record.getAudioFileUrl())
                        : null)
                .transcribedText(record.getTranscribedText())
                .status(record.getStatus() != null ? record.getStatus().name() : null)
                .createdAt(record.getCreatedAt())
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
        String citizenShip = localizationHelper.toTitleCase(protocol.getCitizenship());

        return CaseInterrogationProtocolResponse.builder()
                .fio(localizationHelper.toTitleCase(protocol.getFio()))
                .dateOfBirth(localizationHelper.formatToRussianDate(protocol.getDateOfBirth()))
                .birthPlace(localizationHelper.toTitleCase(protocol.getBirthPlace()))
                .citizenship(citizenShip != null ? "гражданин Республики " + citizenShip : null)
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
                .totalDocuments(caseEntity.getFiles().size())
                .audioUsed(caseEntity.audioUsedCount())
                .totalPages(caseEntity.getFiles().stream()
                        .filter(f -> f.getPages() != null)
                        .mapToInt(CaseFile::getPages)
                        .sum())
                .files(caseEntity.getFiles().stream()
                        .sorted(Comparator
                                .comparing(CaseFile::getTom, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(f -> extractLeadingNumber(f.getOriginalFileName()), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CaseFile::getOriginalFileName, Comparator.nullsLast(Comparator.naturalOrder())))
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
        Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        UserSettingsLanguage language = user.getSettings().getLanguage();
        UserSettingsDto settingsDto = null;
        if (user.getSettings() != null) {
            settingsDto = UserSettingsDto.builder()
                    .level(user.getSettings().getLevel() != null ? user.getSettings().getLevel().name() : null)
                    .language(language.getLanguage())
                    .theme(user.getSettings().getTheme().getTheme())
                    .build();
        }
        Address sterr = null;
        if (user.getRegion() != null && !user.getRegion().getAddresses().isEmpty()) {
            sterr = user.getRegion().getAddresses().get(0);
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
                .rank(user.getRank() != null ? user.getRank().getName() : null)
                .region(localizationHelper.getLocalizedName(user.getRegion(), language))
                .email(user.getEmail())
                .faceEnabled(!faceTemplateRepository.findByUserAndRevokedAtIsNull(user).isEmpty())
                .active(user.isActive())
                .online(user.isOnline(ttl))
                .settings(settingsDto)
                .street(localizationHelper.getLocalizedName(sterr, language))
                .createdCaseCount(user.getCases() != null ? user.getCases().size() : 0)
                .lastSeenAt(formatLastSeen(user.getLastSeenAt()))
                .build();
    }

    public static String formatLastSeen(LocalDateTime lastSeenAt) {
        if (lastSeenAt == null) return null;

        long minutes = ChronoUnit.MINUTES.between(lastSeenAt, LocalDateTime.now());

        if (minutes < 1) return "только что";
        if (minutes < 60) return "был(а) в сети " + minutes + " мин. назад";

        long hours = ChronoUnit.HOURS.between(lastSeenAt, LocalDateTime.now());
        if (hours < 24) return "был(а) в сети " + hours + " ч. назад";

        long days = ChronoUnit.DAYS.between(lastSeenAt, LocalDateTime.now());
        if (days < 30) return "был(а) в сети " + days + " дн. назад";

        return "был(а) в сети давно";
    }

    public FigurantResponse mapToFigurantResponse(CaseFigurant figurant) {
        return FigurantResponse.builder()
                .id(figurant.getId())
                .externalId(figurant.getExternalId())
                .documentType(figurant.getDocumentType())
                .number(figurant.getNumber())
                .fio(figurant.getFio())
                .role(figurant.getRole())
                .details(figurant.getDetails())
                .references(figurant.getReferences() == null ? List.of() :
                        figurant.getReferences().stream()
                                .map(r -> FigurantReferenceResponse.builder()
                                        .id(r.getId())
                                        .referenceId(r.getReferenceId())
                                        .filePath(r.getFilePath())
                                        .build())
                                .toList())
                .build();
    }

    public CaseInterrogationFullResponse mapToInterrogationFullResponse(CaseInterrogation interrogation, User user) {
        String userProf = localizationHelper.getLocalizedName(
                user.getProfession(), user.getSettings().getLanguage());
        String profession = interrogation.getInvestigatorProfession() != null
                ? interrogation.getInvestigatorProfession()
                : userProf;


        String regionSource = interrogation.getInvestigatorRegion() != null
                ? interrogation.getInvestigatorRegion()
                : localizationHelper.getLocalizedName(
                        user.getRegion(), user.getSettings().getLanguage());
        String region = localizationHelper.getGenitive(regionSource);


        String administrationSource = interrogation.getInvestigatorAdministration() != null
                ? interrogation.getInvestigatorAdministration()
                : localizationHelper.getLocalizedName(
                        user.getAdministration(), user.getSettings().getLanguage());
        String administration = localizationHelper.getGenitive(administrationSource);


        String userAdr = localizationHelper.getLocalizedName(
                user.getRegion().getAddresses().get(0), user.getSettings().getLanguage());
        String address = interrogation.getAddrezz() != null
                ? interrogation.getAddrezz()
                : userAdr;

        String city = interrogation.getCity() != null
                ? interrogation.getCity()
                : localizationHelper.extractRegionShortName(user.getRegion().getKzName(), user.getSettings().getLanguage());

        CaseInterrogationProtocolResponse protocolResponse = null;
        String fio = interrogation.getFio();
        if (interrogation.getProtocol() != null) {
            protocolResponse = mapToInterrogationProtocolResponse(interrogation.getProtocol());
            fio = localizationHelper.toTitleCase(interrogation.getProtocol().getFio());
        }

        List<InvolvedPersonsResponse> involvedPersons = interrogation.getInvolvedPersons() != null
                ? interrogation.getInvolvedPersons().stream()
                .map(e -> InvolvedPersonsResponse.builder()
                        .id(e.getId())
                        .type(e.getType())
                        .about(e.getAbout())
                        .build())
                .toList()
                : List.of();

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
                .lawyer(interrogation.getLawyer())
                .state(interrogation.getState())
                .caseNumberState(interrogation.getCaseNumberState())
                .caseNumber(caseNumber)
                .number(interrogation.getNumber())
                .documentType(interrogation.getDocumentType())
                .fio(fio)
                .role(interrogation.getRole())
                .date(interrogation.getDate())
                .involved(interrogation.getInvolved())
                .involvedPersons(involvedPersons)
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
                .completedAt(String.valueOf(f.getCompletedAt()))
                .isQualification(f.isQualification())
                .tom(f.getTom())
                .pages(f.getPages() != null ? f.getPages() : 0)
                .startPage(f.getStartPage())
                .endPage(f.getEndPage())
                .build();
    }

    public CaseInterrogationApplicationFileResponse mapToApplicationFileResponse(CaseInterrogationApplicationFile file) {
        return CaseInterrogationApplicationFileResponse.builder()
                .id(file.getId())
                .displayName(file.getDisplayName())
                .originalFileName(file.getOriginalFileName())
                .storedFileName(file.getStoredFileName())
                .previewUrl(minioService.generatePresignedUrlForPreview(file.getFileUrl()))
                .downloadUrl(minioService.generatePresignedUrlForDownload(file.getFileUrl(), file.getOriginalFileName()))
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .pages(file.getPages() != null ? file.getPages() : 0)
                .uploadedAt(String.valueOf(file.getUploadedAt()))
                .build();
    }

    public QAResponse mapToQAResponse(CaseInterrogationQA caseInterrogationQA) {
        List<AudioRecordResponse> audioRecords = caseInterrogationQA.getAudioRecords().stream()
                .map(this::mapToAudioRecordResponse)
                .toList();

        return QAResponse.builder()
                .id(caseInterrogationQA.getId())
                .question(caseInterrogationQA.getQuestion())
                .answer(caseInterrogationQA.getAnswer())
                .orderIndex(caseInterrogationQA.getOrderIndex())
                .edited(caseInterrogationQA.getIsEdited())
                .status(caseInterrogationQA.getStatus())
                .audioRecords(audioRecords)
                .build();
    }

    public OtherAudioResponse mapToOtherAudioResponse(CaseInterrogationOtherAudio otherAudio) {
        List<AudioRecordResponse> audioRecords = otherAudio.getAudioRecords().stream()
                .map(this::mapToAudioRecordResponse)
                .toList();

        return OtherAudioResponse.builder()
                .id(otherAudio.getId())
                .fieldName(otherAudio.getFieldName())
                .text(otherAudio.getText())
                .status(otherAudio.getStatus())
                .audioRecords(audioRecords)
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
                .profession(appeal.getUser().getProfession() != null ? appeal.getUser().getProfession().getRuName() : null)
                .rank(appeal.getUser().getRank() != null ? appeal.getUser().getRank().getName() : null)
                .administration(appeal.getUser().getAdministration() != null ? appeal.getUser().getAdministration().getRuName() : null)
                .regionId(appeal.getRegion() != null ? appeal.getRegion().getId() : null)
                .regionName(localizationHelper.getLocalizedName(appeal.getRegion(), getCurrentUser().getSettings().getLanguage()))
                .status(appeal.getStatus().getDescription())
                .createdAt(appeal.getCreatedAt())
                .reviewedAt(appeal.getReviewedAt())
                .build();
    }

    public ProfessionDto toProfessionDto(Profession profession) {
        return ProfessionDto.builder()
                .id(profession.getId())
                .name(profession.getRuName())
                .build();
    }

    public RankDto toRankDto(Rank rank) {
        return RankDto.builder()
                .id(rank.getId())
                .name(rank.getName())
                .build();
    }

    public RegionDto toRegionDto(Region region) {
        return RegionDto.builder()
                .id(region.getId())
                .name(region.getRuName())
                .build();
    }

    public AdministrationDto toAdministrationDto(Administration administration) {
        return AdministrationDto.builder()
                .id(administration.getId())
                .name(administration.getRuName())
                .build();
    }

    public LogDto toLogDto(Log log) {
        return LogDto.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .level(log.getLevel().name())
                .action(log.getAction().getDescription())
                .description(log.getDescription())
                .caseNumber(log.getCaseNumber())
                .email(log.getEmail())
                .ipAddress(log.getIpAddress())
                .build();
    }

    public CaseListResponse mapToCaseListResponse(Case c) {
        return CaseListResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .number(c.getNumber())
                .status(c.isStatus())
                .totalDocuments(c.getFiles().size())
                .totalPages(c.getFiles().stream()
                        .filter(f -> f.getPages() != null)
                        .mapToInt(CaseFile::getPages)
                        .sum())
                .totalInterrogations(c.getInterrogations().size())
                .audioInterrogations(c.audioUsedCount())
                .createdDate(c.getCreatedDate())
                .lastActivityDate(c.getLastActivityDate())
                .lastActivityType(c.getLastActivityType())
                .ownerEmail(c.getOwner() != null ? c.getOwner().getEmail() : null)
                .build();
    }

    public SupportTicketDto mapToSupportTicketDto(SupportTicket ticket) {
        List<SupportTicketPhotoDto> photos = ticket.getPhotos().stream()
                .map(p -> SupportTicketPhotoDto.builder()
                        .id(p.getId())
                        .originalFileName(p.getOriginalFileName())
                        .contentType(p.getContentType())
                        .previewUrl(minioService.generatePresignedUrlForPreview(p.getFileUrl()))
                        .downloadUrl(minioService.generatePresignedUrlForDownload(p.getFileUrl(), p.getOriginalFileName()))
                        .build())
                .toList();

        User user = ticket.getUser();
        return SupportTicketDto.builder()
                .id(ticket.getId())
                .fio(user.getSurname() + " " + user.getName() + " " + user.getFathername())
                .region(user.getRegion().getRuName())
                .profession(user.getProfession().getRuName())
                .message(ticket.getMessage())
                .phoneNumber(ticket.getPhoneNumber())
                .createdAt(ticket.getCreatedAt())
                .photos(photos)
                .build();
    }

    public ReviewDto mapToReviewDto(Review review) {
        String previewUrl = review.getFileUrl() != null
                ? minioService.generatePresignedUrlForPreview(review.getFileUrl())
                : null;
        String downloadUrl = review.getFileUrl() != null
                ? minioService.generatePresignedUrlForDownload(review.getFileUrl(), review.getOriginalFileName())
                : null;

        User user = review.getUser();
        return ReviewDto.builder()
                .id(review.getId())
                .fio(user.getSurname() + " " + user.getName() + " " + user.getFathername())
                .region(user.getRegion().getRuName())
                .profession(user.getProfession().getRuName())
                .subject(review.getSubject())
                .message(review.getMessage())
                .createdAt(review.getCreatedAt())
                .originalFileName(review.getOriginalFileName())
                .contentType(review.getContentType())
                .previewUrl(previewUrl)
                .downloadUrl(downloadUrl)
                .build();
    }

    public PlanApprovalHistoryDto toPlanApprovalHistoryDto(PlanApprovalHistory h) {
        User reviewer = h.getReviewer();
        return PlanApprovalHistoryDto.builder()
                .id(h.getId())
                .approvalLevel(h.getApprovalLevel())
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .reviewerName(reviewer != null
                        ? reviewer.getSurname() + " " + reviewer.getName().charAt(0) + "."
                        : null)
                .reviewerProfession(reviewer != null && reviewer.getProfession() != null
                        ? reviewer.getProfession().getRuName()
                        : null)
                .comment(h.getComment())
                .reviewedAt(h.getReviewedAt())
                .build();
    }

    public ManagementPendingPlanDto toManagementPendingPlanDto(Case c, Map<String, Object> enrichedPlan) {
        User author = c.getOwner();
        return ManagementPendingPlanDto.builder()
                .author(author.getSurname() + " " + author.getName().charAt(0))
                .caseNumber(c.getNumber())
                .caseTitle(c.getTitle())
                .planStatus(c.getPlanStatus())
                .planSubmittedAt(c.getPlanSubmittedAt())
                .plan(enrichedPlan)
                .build();
    }

    public PlanEditHistoryDto toPlanEditHistoryDto(PlanEditHistory h) {
        User editor = h.getEditor();
        return PlanEditHistoryDto.builder()
                .id(h.getId())
                .editorName(editor.getSurname() + " " + editor.getName().charAt(0) + ".")
                .actionNumber(h.getActionNumber())
                .fieldKey(h.getFieldKey())
                .oldValue(h.getOldValue())
                .newValue(h.getNewValue())
                .editedAt(h.getEditedAt())
                .build();
    }
}
