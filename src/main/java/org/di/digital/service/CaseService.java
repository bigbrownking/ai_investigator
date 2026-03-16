package org.di.digital.service;

import org.di.digital.dto.request.AddFigurantToCaseRequest;
import org.di.digital.dto.request.CreateCaseRequest;
import org.di.digital.dto.request.FileType;
import org.di.digital.dto.response.CaseFileResponse;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.CaseUserResponse;
import org.di.digital.dto.response.FigurantResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CaseService {
    List<CaseResponse> getUserCases(String username, String sort);
    CaseResponse getCaseById(Long id, String username);
    CaseResponse createCase(CreateCaseRequest request, String username);
    void updateCaseStatus(Long caseId, boolean status, String email);
    InputStreamResource downloadFile(Long caseId, String fileUrl, String email);
    List<CaseFileResponse> addFilesToCase(Long caseId, List<MultipartFile> files, FileType type, String email);
    void deleteFileFromCase(Long caseId, String fileName, String email);
    CaseUserResponse addUserToCase(Long caseId, String userEmailToAdd, String currentUserEmail);
    FigurantResponse addFigurantToCase(Long caseId, AddFigurantToCaseRequest request, String currentUserEmail);

    void removeUserFromCase(Long caseId, Long userId, String currentUserEmail);
    void removeFigurantFromCase(Long caseId, Long figurantId, String currentUserEmail);

    List<CaseUserResponse> getCaseUsers(Long caseId, String currentUserEmail);
    List<FigurantResponse> getCaseFigurants(Long caseId, String email);

    Optional<FigurantResponse> findFigurantByNumber(Long caseId, String documentType, String number, String email);

        // history and activity
    Page<CaseResponse> getRecentCases(String userEmail, int page, int size);
    Page<CaseResponse> getCasesByActivityType(String userEmail, String activityType, int page, int size);
    void updateCaseActivity(String caseNumber, String activityType);
}
