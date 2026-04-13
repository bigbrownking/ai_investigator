package org.di.digital.service;

import org.di.digital.dto.response.*;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AdminService {
    Page<UserProfile> getAllUsers(int page, int size, Long regionId);
    Page<CaseResponse> getAllCases(int page, int size, Long regionId);
    Page<CaseResponse> getUserCases(Long userId, int page, int size);
    AdminStatsDto getStats();
    void activateUser(Long userId);
    void deactivateUser(Long userId);
    Page<AppealDto> getAllAppeals(int page, int size, Long regionId);
    List<RegionStatsDto> getRegionMapStats();
    RegionSummaryDto getRegionSummary(Long regionId, int page, int size);
    CaseResponse getCaseDetail(Long caseId);
}
