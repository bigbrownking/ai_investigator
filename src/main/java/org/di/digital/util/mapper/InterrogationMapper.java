package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.interrogation.*;
import org.di.digital.model.interrogation.*;
import org.di.digital.model.user.User;
import org.di.digital.util.LocalizationHelper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InterrogationMapper {

    private final FileUrlResolver fileUrls;
    private final LocalizationHelper localizationHelper;
    private final InterrogationProtocolMapper protocolMapper;

    public CaseInterrogationResponse toResponse(CaseInterrogation interrogation) {
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
                .ownerFio(interrogation.getUserEntity().getFio())
                .build();
    }

    public CaseInterrogationQAResponse toQAResponse(CaseInterrogationQA qa) {
        return CaseInterrogationQAResponse.builder()
                .id(qa.getId())
                .interrogationId(qa.getInterrogation().getId())
                .question(qa.getQuestion())
                .answer(qa.getAnswer())
                .status(qa.getStatus().name())
                .createAt(qa.getCreatedAt())
                .audioRecords(mapAudioRecords(qa.getAudioRecords()))
                .build();
    }

    public QAResponse toShortQAResponse(CaseInterrogationQA qa) {
        return QAResponse.builder()
                .id(qa.getId())
                .question(qa.getQuestion())
                .answer(qa.getAnswer())
                .orderIndex(qa.getOrderIndex())
                .edited(qa.getIsEdited())
                .status(qa.getStatus())
                .audioRecords(mapAudioRecords(qa.getAudioRecords()))
                .build();
    }

    public OtherAudioResponse toOtherAudioResponse(CaseInterrogationOtherAudio otherAudio) {
        return OtherAudioResponse.builder()
                .id(otherAudio.getId())
                .fieldName(otherAudio.getFieldName())
                .text(otherAudio.getText())
                .status(otherAudio.getStatus())
                .audioRecords(mapAudioRecords(otherAudio.getAudioRecords()))
                .build();
    }

    public AudioRecordResponse toAudioRecordResponse(CaseInterrogationAudioRecord record) {
        return AudioRecordResponse.builder()
                .id(record.getId())
                .audioUrl(fileUrls.preview(record.getAudioFileUrl()))
                .transcribedText(record.getTranscribedText())
                .status(record.getStatus() != null ? record.getStatus().name() : null)
                .createdAt(record.getCreatedAt())
                .build();
    }

    public CaseInterrogationApplicationFileResponse toApplicationFileResponse(CaseInterrogationApplicationFile file) {
        return CaseInterrogationApplicationFileResponse.builder()
                .id(file.getId())
                .displayName(file.getDisplayName())
                .originalFileName(file.getOriginalFileName())
                .storedFileName(file.getStoredFileName())
                .previewUrl(fileUrls.preview(file.getFileUrl()))
                .downloadUrl(fileUrls.download(file.getFileUrl(), file.getOriginalFileName()))
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .pages(file.getPages() != null ? file.getPages() : 0)
                .uploadedAt(String.valueOf(file.getUploadedAt()))
                .build();
    }

    public CaseInterrogationFullResponse toFullResponse(CaseInterrogation interrogation, User user) {
        InterrogatorContext ctx = InterrogatorContext.resolve(interrogation, user, localizationHelper);

        String fio = interrogation.getFio();
        CaseInterrogationProtocolResponse protocolResponse = null;
        if (interrogation.getProtocol() != null) {
            protocolResponse = protocolMapper.toResponse(interrogation.getProtocol());
            fio = localizationHelper.toTitleCase(interrogation.getProtocol().getFio());
        }

        return CaseInterrogationFullResponse.builder()
                .id(interrogation.getId())
                .room(interrogation.getRoom())
                .city(ctx.city())
                .personYear(interrogation.getPersonYear())
                .personSpecialist(interrogation.getPersonSpecialist())
                .personTranslator(interrogation.getPersonTranslator())
                .addrezz(ctx.address())
                .notificationNumber(interrogation.getNotificationNumber())
                .notificationDate(interrogation.getNotificationDate())
                .lawyer(interrogation.getLawyer())
                .state(interrogation.getState())
                .caseNumberState(interrogation.getCaseNumberState())
                .caseNumber(interrogation.getCaseEntity().getNumber())
                .number(interrogation.getNumber())
                .documentType(interrogation.getDocumentType())
                .fio(fio)
                .role(interrogation.getRole())
                .date(interrogation.getDate())
                .involved(interrogation.getInvolved())
                .involvedPersons(mapInvolvedPersons(interrogation))
                .testimony(interrogation.getTestimony())
                .confession(interrogation.getConfession())
                .confessionText(interrogation.getConfessionText())
                .language(normalizeLanguage(interrogation.getLanguage()))
                .translator(interrogation.getTranslator())
                .defender(interrogation.getDefender())
                .familiarization(interrogation.getFamiliarization())
                .additionalInfo(interrogation.getAdditionalInfo())
                .additionalText(interrogation.getAdditionalText())
                .application(interrogation.getApplication())
                .investigator(ctx.investigator())
                .investigatorProfession(ctx.profession())
                .investigatorAdministration(ctx.administration())
                .investigatorRegion(ctx.region())
                .status(interrogation.getStatus().name())
                .protocol(protocolResponse)
                .startedAt(interrogation.getStartedAt())
                .finishedAt(interrogation.getFinishedAt())
                .durationSeconds(interrogation.getDurationSeconds())
                .timerSessions(mapTimerSessions(interrogation))
                .qaList(mapQaList(interrogation))
                .applications(mapApplicationFiles(interrogation))
                .isDop(interrogation.getIsDop())
                .categoryConfirmed(interrogation.getCategoryConfirmed())
                .limitProfile(enumName(interrogation.getLimitProfile()))
                .specialGround(enumName(interrogation.getSpecialGround()))
                .onBreak(interrogation.getOnBreak())
                .breakStartedAt(interrogation.getBreakStartedAt())
                .build();
    }

    // --- helpers ---

    private List<AudioRecordResponse> mapAudioRecords(List<CaseInterrogationAudioRecord> records) {
        return records.stream().map(this::toAudioRecordResponse).toList();
    }

    private List<InvolvedPersonsResponse> mapInvolvedPersons(CaseInterrogation interrogation) {
        if (interrogation.getInvolvedPersons() == null) return List.of();
        return interrogation.getInvolvedPersons().stream()
                .map(e -> InvolvedPersonsResponse.builder()
                        .id(e.getId()).type(e.getType()).about(e.getAbout()).build())
                .toList();
    }

    private List<CaseInterrogationQAResponse> mapQaList(CaseInterrogation interrogation) {
        if (interrogation.getQaList() == null || interrogation.getQaList().isEmpty()) return null;
        return interrogation.getQaList().stream().map(this::toQAResponse).toList();
    }

    private List<InterrogationTimerSessionResponse> mapTimerSessions(CaseInterrogation interrogation) {
        if (interrogation.getTimerSessions() == null) return null;
        return interrogation.getTimerSessions().stream()
                .map(s -> InterrogationTimerSessionResponse.builder()
                        .startedAt(s.getStartedAt()).pausedAt(s.getPausedAt()).build())
                .toList();
    }

    private List<CaseInterrogationApplicationFileResponse> mapApplicationFiles(CaseInterrogation interrogation) {
        if (interrogation.getApplicationFiles() == null || interrogation.getApplicationFiles().isEmpty()) return null;
        return interrogation.getApplicationFiles().stream().map(this::toApplicationFileResponse).toList();
    }

    private String normalizeLanguage(String language) {
        return (language == null || language.equals("русском")) ? "русском" : "казахском";
    }

    private String enumName(Enum<?> e) {
        return e != null ? e.name() : null;
    }
}