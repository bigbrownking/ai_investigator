package org.di.digital.service;

import org.di.digital.dto.request.AddInterrogationRequest;
import org.di.digital.dto.request.CreateCaseRequest;
import org.di.digital.dto.request.FileType;
import org.di.digital.dto.response.CaseInterrogationResponse;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.CaseUserResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface CaseService {
    List<CaseResponse> getUserCases(String username);
    CaseResponse getCaseById(Long id, String username);
    CaseResponse createCase(CreateCaseRequest request, String username);
    InputStreamResource downloadFile(Long caseId, String fileUrl, String email);
    CaseResponse addInterrogation(Long caseId, AddInterrogationRequest request, String email);
    List<CaseInterrogationResponse> searchInterrogations(Long caseId, String role, String fio, LocalDate date, String email);
    CaseResponse addFilesToCase(Long caseId, List<MultipartFile> files, FileType type, String email);
    CaseResponse deleteFileFromCase(Long caseId, String fileName, String email);
    CaseResponse addUserToCase(Long caseId, String userEmailToAdd, String currentUserEmail);

    CaseResponse removeUserFromCase(Long caseId, Long userId, String currentUserEmail);

    List<CaseUserResponse> getCaseUsers(Long caseId, String currentUserEmail);
}
