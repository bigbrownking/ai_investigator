package org.di.digital.service.cases;

import org.di.digital.dto.request.cases.ChangeCaseLanguageRequest;
import org.di.digital.dto.request.interrogation.AddFigurantToCaseRequest;
import org.di.digital.dto.request.cases.CreateCaseRequest;
import org.di.digital.dto.request.cases.EditCaseRequest;
import org.di.digital.dto.request.cases.ReorderCaseFilesRequest;
import org.di.digital.dto.response.cases.*;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.FileType;
import org.di.digital.dto.response.interrogation.FigurantResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CaseService {
    Case getCaseEntityById(Long caseId, String email);
    List<CasePreviewResponse> getUserCases(String username, String sort);
    CaseResponse editCase(Long caseId, EditCaseRequest request, String email);
    CaseResponse changeCaseLanguage(Long caseId, ChangeCaseLanguageRequest request, String email);
    CaseResponse getCaseById(Long id, String email);
    GroupedCaseFileResponse getGroupedCaseFilesById(Long id, String email);
    GroupedCaseFileResponse reorderCaseFiles(Long caseId, ReorderCaseFilesRequest request, String email);
    GroupedCaseFileResponse recalculateToms(Long caseId, String email);
    CaseResponse createCase(CreateCaseRequest request, String username);
    void updateCaseStatus(Long caseId, boolean status, String email);
    InputStreamResource downloadFile(Long caseId, String fileUrl, String email);
    List<CaseFileResponse> addFilesToCase(Long caseId, List<MultipartFile> files, FileType type, String email);
    void deleteFileFromCase(Long caseId, String fileName, String email);
    CaseUserResponse addUserToCase(Long caseId, Long id, String currentUserEmail);
    List<CaseMemberHistoryDto> getMemberHistory(Long caseId, String currentUserEmail);
    List<UserSuggestionResponse> searchUsers(String query);
    FigurantResponse addFigurantToCase(Long caseId, AddFigurantToCaseRequest request, String currentUserEmail);

    void removeUserFromCase(Long caseId, Long userId, String currentUserEmail);
    void removeFigurantFromCase(Long caseId, Long figurantId, String currentUserEmail);

    List<CaseUserResponse> getCaseUsers(Long caseId, String currentUserEmail);
    List<FigurantResponse> getCaseFigurants(Long caseId, String email);

    Optional<FigurantResponse> findFigurantByNumber(Long caseId, String documentType, String number, String email);

    Page<CaseResponse> getRecentCases(String userEmail, int page, int size);
    Page<CaseResponse> getCasesByActivityType(String userEmail, String activityType, int page, int size);
    void updateCaseActivity(String caseNumber, String activityType);
    void deleteAllFiles(Long caseId, String currentEmail);
    void deleteCaseById(Long id, String currentEmail);

    CaseFileResponse getFileByName(Long caseId, String fileName, String email);


    // Migration methods
    void migrateAllCaseToms();
    void recalculateAllPages();
}
