package org.di.digital.service;

import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.UpdateProtocolFieldRequest;
import org.di.digital.dto.response.CaseInterrogationFullResponse;
import org.di.digital.dto.response.CaseInterrogationResponse;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.QAResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface CaseInterrogationService {
    CaseResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email);

    CaseResponse deleteInterrogation(Long caseId, Long interrogationId, String email);

    List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, LocalDate date, String email);

    void updateProtocolField(Long caseId, Long interrogationId, UpdateProtocolFieldRequest request, String email);

    QAResponse uploadAudioAndEnqueue(Long caseId, Long interrogationId, String question, MultipartFile file, String language, String email);

    List<QAResponse> getQAList(Long caseId, Long interrogationId, String email);

    CaseInterrogationFullResponse getDetailed(long caseId, long interrogationId, String email);

    void completeInterrogation(long caseId, long interrogationId, String email);

    void controlTimer(Long caseId, Long interrogationId, String action, String username);

}
