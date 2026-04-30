package org.di.digital.service;

import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.AppealDto;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.UserProfile;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RegAdminService {
    Page<AppealDto> getMyRegionAppeals(Long adminId, int page, int size, AppealSearchRequest appealSearchRequest);
    void approveAppeal(Long appealId, Long adminId);
    void rejectAppeal(Long appealId, Long adminId);
    Page<UserProfile> getMyRegionUsers(Long adminId, int page, int size, UserSearchRequest userSearchRequest);
    Page<CaseResponse> getUserCases(Long adminId, Long userId, int page, int size, CaseSearchRequest caseSearchRequest);
    Page<CaseResponse> getMyRegionCases(Long adminId, int page, int size, CaseSearchRequest caseSearchRequest);
    CaseResponse getMyRegionCaseDetail(Long adminId, Long caseId);
    String getMyRegionCaseIndictment(Long adminId, Long caseId);
    String getMyRegionCaseQualification(Long adminId, Long caseId);
}
