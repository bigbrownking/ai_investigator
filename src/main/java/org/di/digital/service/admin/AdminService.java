package org.di.digital.service.admin;

import org.di.digital.dto.request.user.UpdateProfileRequest;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.*;
import org.di.digital.dto.response.cases.CasePageResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.plan.CasePlanResponse;
import org.di.digital.dto.response.support.ReviewDto;
import org.di.digital.dto.response.support.SupportTicketDto;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface AdminService {
    PagedUserResponse getAllUsers(int page, int size, UserSearchRequest userSearchRequest);
    CasePageResponse getAllCases(int page, int size, CaseSearchRequest caseSearchRequest);
    CasePageResponse getUserCases(Long userId, int page, int size, CaseSearchRequest caseSearchRequest);
    AdminStatsDto getStats(LocalDate from, LocalDate to);
    List<UserSuggestionResponse> searchUsers(String query);
    void activateUser(Long userId);
    void deactivateUser(Long userId);
    void deleteUser(Long userId);
    PagedAppealResponse getAllAppeals(int page, int size, AppealSearchRequest appealSearchRequest);
    List<RegionStatsDto> getRegionMapStats();
    RegionSummaryDto getRegionSummary(Long regionId, int page, int size);
    CaseResponse getCaseDetail(Long caseId);
    CaseInterrogationFullResponse getInterrogationDetail(Long interrogationId);
    byte[] downloadInterrogation(Long interrogationId);
    void approveAppeal(Long appealId, Long adminId);
    void rejectAppeal(Long appealId, Long adminId);
    Page<LogDto> getUserLogs(String email, int page, int size);
    Page<SupportTicketDto> getAllSupportTickets(int page, int size);
    SupportTicketDto getSupportTicketDetail(Long id);
    Page<ReviewDto> getAllReviews(int page, int size);
    ReviewDto getReviewDetail(Long id);
    void assignAdvancedUserRole(String email);
    void assignRegAdminRole(String email, List<String> regions);
    void removeRegAdminRole(String email, List<String> regions);
    void changeOwner(Long caseId, Long id);
    UserProfile updateUserProfile(Long id, UpdateProfileRequest request);
    //жалал что админ пидр
    String getIndictment(Long caseId);
    String getQualification(Long caseId);
    CasePlanResponse getPlan(Long caseId);

}
