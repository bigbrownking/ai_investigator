package org.di.digital.service.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.model.Appeal;
import org.di.digital.model.Case;
import org.di.digital.model.Region;
import org.di.digital.model.User;
import org.di.digital.model.enums.AppealStatus;
import org.di.digital.repository.*;
import org.di.digital.repository.search.CaseSpecifications;
import org.di.digital.repository.search.UserSpecifications;
import org.di.digital.service.AdminService;
import org.di.digital.util.LocalizationHelper;
import org.di.digital.util.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.di.digital.repository.search.CaseSpecifications.hasOwner;
import static org.di.digital.util.UserUtil.getCurrentUser;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final AppealRepository appealRepository;
    private final RegionRepository regionRepository;
    private final LogRepository logRepository;
    private final Mapper mapper;
    private final LocalizationHelper localizationHelper;

    @Override
    public Page<UserProfile> getAllUsers(int page, int size, UserSearchRequest req) {
        Pageable pageable = PageRequest.of(page, size);
        Specification<User> spec = UserSpecifications.build(req);
        return userRepository.findAll(spec, pageable)
                .map(mapper::mapToUserProfileResponse);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<CaseResponse> getUserCases(Long userId, int page, int size, CaseSearchRequest req) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Pageable pageable = PageRequest.of(page, size);
        Specification<Case> spec = CaseSpecifications.build(req)
                .and(hasOwner(userId));
        return caseRepository.findAll(spec, pageable)
                .map(mapper::mapToCaseResponse);
    }

    @Override
    public Page<CaseResponse> getAllCases(int page, int size, CaseSearchRequest req) {
        Pageable pageable = PageRequest.of(page, size);
        Specification<Case> spec = CaseSpecifications.build(req);
        return caseRepository.findAll(spec, pageable)
                .map(mapper::mapToCaseResponse);
    }

    @Override
    public Page<AppealDto> getAllAppeals(int page, int size, AppealSearchRequest req) {
        Pageable pageable = PageRequest.of(page, size);
        return appealRepository.findAll(pageable)
                .map(mapper::toAppealDto);
    }

    @Override
    public AdminStatsDto getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveTrue();
        long inactiveUsers = userRepository.countByActiveFalse();
        long totalCases = caseRepository.count();
        long pendingAppeals = appealRepository.countByStatus(AppealStatus.PENDING);
        long approvedAppeals = appealRepository.countByStatus(AppealStatus.APPROVED);
        long rejectedAppeals = appealRepository.countByStatus(AppealStatus.REJECTED);

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .totalCases(totalCases)
                .pendingAppeals(pendingAppeals)
                .approvedAppeals(approvedAppeals)
                .rejectedAppeals(rejectedAppeals)
                .build();
    }

    @Override
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        userRepository.save(user);
        log.info("User {} activated by admin", userId);
    }

    @Override
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
        log.info("User {} deactivated by admin", userId);
    }
    @Override
    public List<RegionStatsDto> getRegionMapStats() {
        return regionRepository.findAll().stream()
                .map(region -> RegionStatsDto.builder()
                        .regionId(region.getId())
                        .regionName(localizationHelper.getLocalizedName(region, getCurrentUser().getSettings().getLanguage()))
                        .mapCode(region.getMapCode())
                        .totalUsers(userRepository.countByRegionId(region.getId()))
                        .activeUsers(userRepository.countByRegionIdAndActiveTrue(region.getId()))
                        .totalCases(caseRepository.countByRegionId(region.getId()))
                        .pendingAppeals(appealRepository.countByRegionIdAndStatus(region.getId(), AppealStatus.PENDING))
                        .build()
                ).toList();
    }
    @Override
    public RegionSummaryDto getRegionSummary(Long regionId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new RuntimeException("Region not found: " + regionId));

        RegionStatsDto stats = RegionStatsDto.builder()
                .regionId(region.getId())
                .regionName(localizationHelper.getLocalizedName(region, getCurrentUser().getSettings().getLanguage()))
                .mapCode(region.getMapCode())
                .totalUsers(userRepository.countByRegionId(regionId))
                .activeUsers(userRepository.countByRegionIdAndActiveTrue(regionId))
                .totalCases(caseRepository.countByRegionId(regionId))
                .pendingAppeals(appealRepository.countByRegionIdAndStatus(regionId, AppealStatus.PENDING))
                .build();

        Page<UserProfile> users = userRepository.findByRegionId(regionId, pageable)
                .map(mapper::mapToUserProfileResponse);

        Page<CaseResponse> cases = caseRepository.findByOwnerRegionId(regionId, pageable)
                .map(mapper::mapToCaseResponse);

        Page<AppealDto> appeals = appealRepository.findByRegionId(regionId, pageable)
                .map(mapper::toAppealDto);

        return RegionSummaryDto.builder()
                .stats(stats)
                .users(users)
                .cases(cases)
                .appeals(appeals)
                .build();
    }
    @Override
    @Transactional(readOnly = true)
    public CaseResponse getCaseDetail(Long caseId) {
        return caseRepository.findById(caseId)
                .map(mapper::mapToCaseResponse)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));
    }

    @Override
    public String getCaseQualification(Long caseId) {
        return caseRepository.findById(caseId)
                .map(Case::getQualification)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));
    }

    @Override
    public String getCaseIndictment(Long caseId) {
        return caseRepository.findById(caseId)
                .map(Case::getIndictment)
                .orElseThrow(() -> new RuntimeException("Case not found: " + caseId));
    }

    @Override
    @Transactional
    public void approveAppeal(Long appealId, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("Appeal not found"));

        appeal.setStatus(AppealStatus.APPROVED);
        appeal.setReviewedBy(admin);
        appeal.setReviewedAt(LocalDateTime.now());
        appealRepository.save(appeal);

        User user = appeal.getUser();
        user.setActive(true);
        userRepository.save(user);

        log.info("Appeal {} approved by admin {}", appealId, adminId);
    }

    @Override
    @Transactional
    public void rejectAppeal(Long appealId, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("Appeal not found"));

        appeal.setStatus(AppealStatus.REJECTED);
        appeal.setReviewedBy(admin);
        appeal.setReviewedAt(LocalDateTime.now());
        appealRepository.save(appeal);

        log.info("Appeal {} rejected by admin {}", appealId, adminId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LogDto> getUserLogs(String email, int page, int size) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        return logRepository.findByEmail(email, PageRequest.of(page, size))
                .map(mapper::toLogDto);
    }
}

