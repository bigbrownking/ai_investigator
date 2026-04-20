package org.di.digital.service;

import org.di.digital.dto.response.AppealDto;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.UserProfile;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RegAdminService {
    Page<AppealDto> getMyRegionAppeals(Long adminId, int page, int size);
    void approveAppeal(Long appealId, Long adminId);
    void rejectAppeal(Long appealId, Long adminId);
    Page<UserProfile> getMyRegionUsers(Long adminId, int page, int size);
    Page<CaseResponse> getUserCases(Long adminId, Long userId, int page, int size);
    Page<CaseResponse> getMyRegionCases(Long adminId, int page, int size);
    CaseResponse getMyRegionCaseDetail(Long adminId, Long caseId);
    String getMyRegionCaseIndictment(Long adminId, Long caseId);
    String getMyRegionCaseQualification(Long adminId, Long caseId);
}
