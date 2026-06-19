package org.di.digital.service.impl.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.dto.response.cases.CaseListResponse;
import org.di.digital.dto.response.cases.CasePageResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.cases.Case;
import org.di.digital.model.user.User;
import org.di.digital.model.enums.AppealStatus;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.repository.user.AppealRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.LogRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.search.AppealSpecifications;
import org.di.digital.repository.search.CaseSpecifications;
import org.di.digital.repository.search.UserSpecifications;
import org.di.digital.service.RegAdminService;
import org.di.digital.service.export.interrogation.InterrogationExportService;
import org.di.digital.util.LocalizationHelper;
import org.di.digital.util.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;
import static org.di.digital.util.requests.UserUtil.validateRegionAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegAdminServiceImpl implements RegAdminService {

    private final AppealRepository appealRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final LogRepository logRepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final LocalizationHelper localizationHelper;
    private final InterrogationExportService interrogationExportService;
    private final Mapper mapper;

    @Override
    public Page<AppealDto> getMyRegionAppeals(Long adminId, int page, int size, AppealSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new IllegalStateException("Admin has no region assigned");
        }

        Pageable pageable = PageRequest.of(page, size);
        Specification<Appeal> spec = AppealSpecifications.buildForRegion(
                admin.getRegion().getId(), req
        );
        return appealRepository.findAll(spec, pageable)
                .map(mapper::toAppealDto);
    }

    @Override
    public Page<UserProfile> getMyRegionUsers(Long adminId, int page, int size, UserSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new IllegalStateException("Admin has no region assigned");
        }

        Pageable pageable = PageRequest.of(page, size);
        Specification<User> spec = UserSpecifications.buildForRegion(
                admin.getRegion().getId(), req
        );

        return userRepository.findAll(spec, pageable)
                .map(mapper::mapToUserProfileResponse);
    }

    @Override
    public CasePageResponse getUserCases(Long adminId, Long userId, int page, int size, CaseSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (admin.getRegion() == null || !admin.getRegion().getId().equals(user.getRegion().getId())) {
            throw new IllegalStateException("User is not in your region");
        }

        Specification<Case> spec = CaseSpecifications.build(req)
                .and(CaseSpecifications.hasOwner(userId));

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size))
                .map(mapper::mapToCaseListResponse);

        List<Case> allFiltered = caseRepository.findAll(spec);

        return CasePageResponse.builder()
                .cases(casePage)
                .totalDocuments(allFiltered.stream().mapToLong(c -> c.getFiles().size()).sum())
                .totalInterrogations(allFiltered.stream().mapToLong(c -> c.getInterrogations().size()).sum())
                .audioInterrogations(allFiltered.stream().mapToLong(Case::audioUsedCount).sum())
                .build();
    }

    @Override
    @Transactional
    public void approveAppeal(Long appealId, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new AccessDeniedException("У администратора нет назначенного региона");
        }

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalStateException("Appeal not found"));

        if (appeal.getRegion() == null ||
                !appeal.getRegion().getId().equals(admin.getRegion().getId())) {
            throw new AccessDeniedException("Это обращение не принадлежит вашему региону");
        }

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
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new AccessDeniedException("У администратора нет назначенного региона");
        }

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalStateException("Appeal not found"));

        if (appeal.getRegion() == null ||
                !appeal.getRegion().getId().equals(admin.getRegion().getId())) {
            throw new AccessDeniedException("Это обращение не принадлежит вашему региону");
        }

        appeal.setStatus(AppealStatus.REJECTED);
        appeal.setReviewedBy(admin);
        appeal.setReviewedAt(LocalDateTime.now());
        appealRepository.save(appeal);

        log.info("Appeal {} rejected by admin {}", appealId, adminId);
    }

    @Override
    @Transactional(readOnly = true)
    public CasePageResponse getMyRegionCases(Long adminId, int page, int size, CaseSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new IllegalStateException("Admin has no region assigned");
        }

        Specification<Case> spec = CaseSpecifications.buildForRegion(
                admin.getRegion().getId(), req
        );

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size))
                .map(mapper::mapToCaseListResponse);

        List<Case> allFiltered = caseRepository.findAll(spec);

        return CasePageResponse.builder()
                .cases(casePage)
                .totalDocuments(allFiltered.stream().mapToLong(c -> c.getFiles().size()).sum())
                .totalInterrogations(allFiltered.stream().mapToLong(c -> c.getInterrogations().size()).sum())
                .audioInterrogations(allFiltered.stream().mapToLong(Case::audioUsedCount).sum())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CaseResponse getMyRegionCaseDetail(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found"));

        validateRegionAccess(admin, caseEntity);

        return mapper.mapToCaseResponse(caseEntity);
    }

    @Override
    public String getMyRegionCaseIndictment(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found"));

        validateRegionAccess(admin, caseEntity);

        return caseEntity.getIndictment();
    }

    @Override
    public String getMyRegionCaseQualification(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        validateRegionAccess(admin, caseEntity);

        return caseEntity.getQualification();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LogDto> getMyRegionUserLogs(Long adminId, String email, int page, int size) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new RuntimeException("Admin has no region assigned");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (user.getRegion() == null ||
                !user.getRegion().getId().equals(admin.getRegion().getId())) {
            throw new AccessDeniedException("Этот пользователь не принадлежит вашему региону");
        }

        return logRepository.findByEmail(email, PageRequest.of(page, size))
                .map(mapper::toLogDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseInterrogationFullResponse getMyRegionInterrogationDetail(Long adminId, Long interrogationId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        validateRegionAccess(admin, interrogation.getCaseEntity());

        User owner = interrogation.getCaseEntity().getOwner();
        return mapper.mapToInterrogationFullResponse(interrogation, owner);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadMyRegionInterrogation(Long adminId, Long interrogationId) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        CaseInterrogationFullResponse data = getMyRegionInterrogationDetail(adminId, interrogationId);
        return interrogationExportService.exportToDocx(data, interrogation.getCaseEntity().getOwner());
    }

    @Override
    @Transactional(readOnly = true)
    public RegionStatsDto getMyRegionStats(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new RuntimeException("Admin has no region assigned");
        }

        Long regionId = admin.getRegion().getId();

        return RegionStatsDto.builder()
                .regionId(regionId)
                .regionName(localizationHelper.getLocalizedName(admin.getRegion(), getCurrentUser().getSettings().getLanguage()))
                .mapCode(admin.getRegion().getMapCode())
                .totalUsers(userRepository.countByRegionId(regionId))
                .activeUsers(userRepository.countByRegionIdAndActiveTrue(regionId))
                .totalCases(caseRepository.countByRegionId(regionId))
                .pendingAppeals(appealRepository.countByRegionIdAndStatus(regionId, AppealStatus.PENDING))
                .build();
    }

    @Override
    @Transactional
    public void changeOwner(Long adminId, Long caseId, String newOwnerEmail) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        validateRegionAccess(admin, caseEntity);

        User newOwner = userRepository.findByEmail(newOwnerEmail)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + newOwnerEmail));

        if (newOwner.getRegion() == null ||
                !newOwner.getRegion().getId().equals(admin.getRegion().getId())) {
            throw new AccessDeniedException("Новый владелец не принадлежит вашему региону");
        }

        User oldOwner = caseEntity.getOwner();
        caseEntity.setOwner(newOwner);

        if (caseEntity.hasUser(oldOwner)) {
            caseEntity.removeUser(oldOwner);
        }

        caseRepository.save(caseEntity);

        log.info("Case {} owner changed from {} to {} by admin {}",
                caseId, oldOwner.getEmail(), newOwner.getEmail(), adminId);
    }

    @Override
    public String getMyRegionIndictment(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        validateRegionAccess(admin, caseEntity);

        return caseEntity.getIndictment();
    }

    @Override
    public String getMyRegionQualification(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        validateRegionAccess(admin, caseEntity);

        return caseEntity.getQualification();
    }

    @Override
    public Map<String, Object> getMyRegionPlan(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        validateRegionAccess(admin, caseEntity);

        return caseEntity.getPlan();
    }
}