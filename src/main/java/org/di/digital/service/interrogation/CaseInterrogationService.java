package org.di.digital.service.interrogation;

import org.di.digital.dto.request.interrogation.AddInterrogationRequest;
import org.di.digital.dto.request.interrogation.EditAudioTranscribedTextRequest;
import org.di.digital.dto.request.interrogation.UpdateProtocolFieldRequest;
import org.di.digital.dto.response.interrogation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface CaseInterrogationService {
    CaseInterrogationFullResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email);
    QAResponse createQA(Long caseId, Long interrogationId, String email);

    void deleteInterrogation(Long caseId, Long interrogationId, String email);

    List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, Boolean isDop, LocalDate date, String email);

    void updateProtocolField(Long caseId, Long interrogationId, UpdateProtocolFieldRequest request, String email);
    void updateOtherField(Long caseId, Long interrogationId, UpdateProtocolFieldRequest request,  String email);
    QAResponse uploadAudioAndEnqueue(Long caseId, Long interrogationId, Long qaId, MultipartFile file, String email);
    OtherAudioResponse uploadOtherAudioAndEnqueue(Long caseId, Long interrogationId, Long qaId, String fieldName, MultipartFile file, String language, String email);
    QAResponse editTranscribedText(Long caseId, Long interrogationId, EditAudioTranscribedTextRequest request, String email);
    OtherAudioResponse editOtherAudioText(Long caseId, Long interrogationId, Long otherAudioId, String text, String email);
    List<QAResponse> getQAList(Long caseId, Long interrogationId, String email);

    CaseInterrogationFullResponse getDetailed(long caseId, long interrogationId, String email);

    void completeInterrogation(long caseId, long interrogationId, String email);

    void controlTimer(Long caseId, Long interrogationId, String action, String username);

    List<CaseInterrogationApplicationFileResponse> uploadApplicationFiles(
            Long caseId, Long interrogationId, List<MultipartFile> files,
            Map<String, String> displayNames, String email);
    void deleteApplicationFile(Long caseId, Long interrogationId, Long fileId, String email);

}
