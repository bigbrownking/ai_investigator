package org.di.digital.service;

import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AdminService {
    Page<UserProfile> getAllUsers(int page, int size, UserSearchRequest userSearchRequest);
    Page<CaseResponse> getAllCases(int page, int size, CaseSearchRequest caseSearchRequest);
    Page<CaseResponse> getUserCases(Long userId, int page, int size, CaseSearchRequest caseSearchRequest);
    AdminStatsDto getStats();
    void activateUser(Long userId);
    void deactivateUser(Long userId);
    Page<AppealDto> getAllAppeals(int page, int size, AppealSearchRequest appealSearchRequest);
    List<RegionStatsDto> getRegionMapStats();
    RegionSummaryDto getRegionSummary(Long regionId, int page, int size);
    CaseResponse getCaseDetail(Long caseId);
    String getCaseIndictment(Long caseId);
    String getCaseQualification(Long caseId);
}
