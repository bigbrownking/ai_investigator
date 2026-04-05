package org.di.digital.service;

import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.EditAudioTranscribedTextRequest;
import org.di.digital.dto.request.UpdateProtocolFieldRequest;
import org.di.digital.dto.response.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface CaseInterrogationService {
    CaseInterrogationFullResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email);

    void deleteInterrogation(Long caseId, Long interrogationId, String email);

    List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, Boolean isDop, LocalDate date, String email);

    void updateProtocolField(Long caseId, Long interrogationId, UpdateProtocolFieldRequest request, String email);
    void updateOtherField(Long caseId, Long interrogationId, UpdateProtocolFieldRequest request,  String email);
    QAResponse uploadAudioAndEnqueue(Long caseId, Long interrogationId, String question, MultipartFile file, String language, String email);
    OtherAudioResponse uploadOtherAudioAndEnqueue(Long caseId, Long interrogationId, String fieldName, MultipartFile file, String language, String email);
    QAResponse editTranscribedText(Long caseId, Long interrogationId, EditAudioTranscribedTextRequest request, String email);
    List<QAResponse> getQAList(Long caseId, Long interrogationId, String email);

    CaseInterrogationFullResponse getDetailed(long caseId, long interrogationId, String email);

    void completeInterrogation(long caseId, long interrogationId, String email);

    void controlTimer(Long caseId, Long interrogationId, String action, String username);

    List<CaseInterrogationApplicationFileResponse> uploadApplicationFiles(Long caseId, Long interrogationId, List<MultipartFile> files, String email);
    void deleteApplicationFile(Long caseId, Long interrogationId, Long fileId, String email);

}
