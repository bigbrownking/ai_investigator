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
import org.di.digital.dto.response.cases.RejectionReasonResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.cases.Case;
import org.di.digital.model.user.Region;
import org.di.digital.model.user.User;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.repository.user.AppealRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.cases.RejectionReasonStatusRepository;
import org.di.digital.repository.LogRepository;
import org.di.digital.repository.user.RegionRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.search.AppealSpecifications;
import org.di.digital.repository.search.CaseSpecifications;
import org.di.digital.repository.search.UserSpecifications;
import org.di.digital.service.admin.RegAdminService;
import org.di.digital.service.export.interrogation.InterrogationExportService;
import org.di.digital.util.mapper.CaseMapper;
import org.di.digital.util.mapper.InterrogationMapper;
import org.di.digital.util.mapper.SupportMapper;
import org.di.digital.util.mapper.UserMapper;
import org.di.digital.util.requests.UserUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegAdminServiceImpl implements RegAdminService {

    private final AppealRepository appealRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final LogRepository logRepository;
    private final UserMapper userMapper;
    private final CaseMapper caseMapper;
    private final InterrogationMapper interrogationMapper;
    private final SupportMapper supportMapper;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final InterrogationExportService interrogationExportService;
    private final UserUtil userUtil;
    private final RejectionReasonStatusRepository rejectionReasonStatusRepository;

    @Override
    public Page<AppealDto> getMyRegionAppeals(Long adminId, int page, int size, AppealSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        List<Region> adminRegions = userUtil.getAdminRegions(admin);

        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();

        Pageable pageable = PageRequest.of(page, size);
        Specification<Appeal> spec = AppealSpecifications.buildForRegions(regionIds, req);
        return appealRepository.findAll(spec, pageable).map(supportMapper::toAppealDto);
    }

    @Override
    public Page<UserProfile> getMyRegionUsers(Long adminId, int page, int size, UserSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        List<Region> adminRegions = userUtil.getAdminRegions(admin);

        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();

        Pageable pageable = PageRequest.of(page, size);
        Specification<User> spec = UserSpecifications.buildForRegions(regionIds, req);
        return userRepository.findAll(spec, pageable).map(userMapper::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public CasePageResponse getUserCases(Long adminId, Long userId, int page, int size, CaseSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        userUtil.validateUserRegionAccess(admin, user);

        Specification<Case> spec = CaseSpecifications.build(req)
                .and(CaseSpecifications.hasOwner(userId));

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size))
                .map(caseMapper::toListResponse);

        return caseMapper.build(spec, casePage);
    }

    @Override
    @Transactional
    public void approveAppeal(Long appealId, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.APPEAL.localized(currentLang(), appealId.toString())));

        userUtil.validateAppealRegionAccess(admin, appeal);

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
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.APPEAL.localized(currentLang(), appealId.toString())));

        userUtil.validateAppealRegionAccess(admin, appeal);

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
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        List<Long> regionIds = userUtil.getAdminRegions(admin).stream()
                .map(Region::getId)
                .toList();

        Specification<Case> spec = CaseSpecifications.buildForRegions(regionIds, req);

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size))
                .map(caseMapper::toListResponse);

        return caseMapper.build(spec, casePage);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseResponse getMyRegionCaseDetail(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        return caseMapper.toResponse(caseEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LogDto> getMyRegionUserLogs(Long adminId, String email, int page, int size) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

        userUtil.validateUserRegionAccess(admin, user);

        return logRepository.findByEmail(email, PageRequest.of(page, size))
                .map(supportMapper::toLogDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseInterrogationFullResponse getMyRegionInterrogationDetail(Long adminId, Long interrogationId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(currentLang(), interrogationId.toString())));

        userUtil.validateRegionAccess(admin, interrogation.getCaseEntity());

        User owner = interrogation.getCaseEntity().getOwner();
        return interrogationMapper.toFullResponse(interrogation, owner);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadMyRegionInterrogation(Long adminId, Long interrogationId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.INTERROGATION.localized(currentLang(), interrogationId.toString())));

        userUtil.validateRegionAccess(admin, interrogation.getCaseEntity());

        CaseInterrogationFullResponse data = getMyRegionInterrogationDetail(adminId, interrogationId);
        return interrogationExportService.exportToDocx(data, interrogation.getCaseEntity().getOwner());
    }

    @Override
    @Transactional(readOnly = true)
    public RegionStatsDto getMyRegionStats(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        List<Region> adminRegions = userUtil.getAdminRegions(admin);

        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();

        return RegionStatsDto.builder()
                .totalUsers(userRepository.countByRegionIdIn(regionIds))
                .activeUsers(userRepository.countByRegionIdInAndActiveTrue(regionIds))
                .totalCases(caseRepository.countByRegionIdIn(regionIds))
                .pendingAppeals(appealRepository.countByRegionIdInAndStatus(regionIds, AppealStatus.PENDING))
                .build();
    }

    @Override
    @Transactional
    public void changeOwner(Long adminId, Long caseId, Long id) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        User newOwner = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), id.toString())));

        if (!newOwner.isActive()) {
            throw new IllegalStateException(MessageConstant.USER_IS_NOT_ACTIVE.format(currentLang()));
        }

        userUtil.validateUserRegionAccess(admin, newOwner);

        User oldOwner = caseEntity.getOwner();
        caseEntity.setOwner(newOwner);

        if (oldOwner != null && caseEntity.hasUser(oldOwner)) {
            caseEntity.removeUser(oldOwner);
        }

        if (!caseEntity.hasUser(newOwner)) {
            caseEntity.addUser(newOwner);
        }

        caseRepository.save(caseEntity);

        log.info("Case {} owner changed from {} to {} by admin {}",
                caseId, oldOwner != null ? oldOwner.getEmail() : "null", newOwner.getEmail(), adminId);
    }

    @Override
    public List<UserSuggestionResponse> searchUsers(Long adminId, String query) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        List<Region> adminRegions = userUtil.getAdminRegions(admin);
        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();

        if (regionIds.isEmpty()) {
            return List.of();
        }

        return userRepository.searchAllUsersByRegions(regionIds, query)
                .stream()
                .map(user -> UserSuggestionResponse.builder()
                        .id(user.getId())
                        .fio(String.join(" ",
                                        Optional.ofNullable(user.getSurname()).orElse(""),
                                        Optional.ofNullable(user.getName()).orElse(""),
                                        Optional.ofNullable(user.getFathername()).orElse(""))
                                .trim()
                                .replaceAll("\\s+", " "))
                        .email(user.getEmail())
                        .build())
                .toList();
    }

    @Override
    public String getMyRegionIndictment(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        return caseEntity.getIndictment();
    }

    @Override
    public String getMyRegionQualification(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        return caseEntity.getQualification();
    }

    @Override
    public Map<String, Object> getMyRegionPlan(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        return caseEntity.getPlan();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RejectionReasonResponse> getRejectionReasonResponseHistory(Long caseId, Long adminId, String email) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        List<Region> adminRegions = userUtil.getAdminRegions(admin);
        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();

        if (regionIds.isEmpty()) return List.of();

        List<Long> caseIds = caseRepository.findByOwnerRegionIdIn(regionIds)
                .stream()
                .map(Case::getId)
                .toList();

        if (caseIds.isEmpty()) return List.of();

        return rejectionReasonStatusRepository
                .findAllByCaseIdInOrderByTimestampDesc(caseIds)
                .stream()
                .map(caseMapper::toRejectionReasonResponse)
                .toList();
    }

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}