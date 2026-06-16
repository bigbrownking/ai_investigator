package org.di.digital.service;

import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.AdminStatsDto;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.dto.response.admin.RegionSummaryDto;
import org.di.digital.dto.response.cases.CasePageResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.support.ReviewDto;
import org.di.digital.dto.response.support.SupportTicketDto;
import org.di.digital.dto.response.user.UserProfile;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AdminService {
    Page<UserProfile> getAllUsers(int page, int size, UserSearchRequest userSearchRequest);
    CasePageResponse getAllCases(int page, int size, CaseSearchRequest caseSearchRequest);
    CasePageResponse getUserCases(Long userId, int page, int size, CaseSearchRequest caseSearchRequest);
    AdminStatsDto getStats();
    void activateUser(Long userId);
    void deactivateUser(Long userId);
    Page<AppealDto> getAllAppeals(int page, int size, AppealSearchRequest appealSearchRequest);
    List<RegionStatsDto> getRegionMapStats();
    RegionSummaryDto getRegionSummary(Long regionId, int page, int size);
    CaseResponse getCaseDetail(Long caseId);
    CaseInterrogationFullResponse getInterrogationDetail(Long interrogationId);
    byte[] downloadInterrogation(Long interrogationId);
    String getCaseIndictment(Long caseId);
    String getCaseQualification(Long caseId);
    void approveAppeal(Long appealId, Long adminId);
    void rejectAppeal(Long appealId, Long adminId);
    Page<LogDto> getUserLogs(String email, int page, int size);
    Page<SupportTicketDto> getAllSupportTickets(int page, int size);
    SupportTicketDto getSupportTicketDetail(Long id);
    Page<ReviewDto> getAllReviews(int page, int size);
    ReviewDto getReviewDetail(Long id);
    void assignAdvancedUserRole(Long userId);
    void assignRegAdminRole(Long userId);
}
