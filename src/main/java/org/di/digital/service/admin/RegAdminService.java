package org.di.digital.service.admin;

import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.dto.response.cases.CasePageResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface RegAdminService {
    Page<AppealDto> getMyRegionAppeals(Long adminId, int page, int size, AppealSearchRequest appealSearchRequest);

    void approveAppeal(Long appealId, Long adminId);

    void rejectAppeal(Long appealId, Long adminId);

    Page<UserProfile> getMyRegionUsers(Long adminId, int page, int size, UserSearchRequest userSearchRequest);

    CasePageResponse getUserCases(Long adminId, Long userId, int page, int size, CaseSearchRequest caseSearchRequest);

    CasePageResponse getMyRegionCases(Long adminId, int page, int size, CaseSearchRequest caseSearchRequest);

    CaseResponse getMyRegionCaseDetail(Long adminId, Long caseId);

    Page<LogDto> getMyRegionUserLogs(Long adminId, String email, int page, int size);

    CaseInterrogationFullResponse getMyRegionInterrogationDetail(Long adminId, Long interrogationId);

    byte[] downloadMyRegionInterrogation(Long adminId, Long interrogationId);

    RegionStatsDto getMyRegionStats(Long adminId);

    void changeOwner(Long adminId, Long caseId, Long id);
    List<UserSuggestionResponse> searchUsers(Long adminId, String query);

    String getMyRegionIndictment(Long adminId, Long caseId);

    String getMyRegionQualification(Long adminId, Long caseId);

    Map<String, Object> getMyRegionPlan(Long adminId, Long caseId);
}
