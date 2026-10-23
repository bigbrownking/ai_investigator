package org.di.digital.service.impl.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.LogDto;
import org.di.digital.dto.response.admin.AdminStatsDto;
import org.di.digital.dto.response.admin.AppealDto;
import org.di.digital.dto.response.admin.RegionStatsDto;
import org.di.digital.dto.response.cases.*;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.di.digital.model.enums.file.CaseFileStatusEnum;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.report.CaseReport;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.user.Region;
import org.di.digital.model.user.Role;
import org.di.digital.model.user.User;
import org.di.digital.repository.LogRepository;
import org.di.digital.model.user.*;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.repository.review.CaseReportRepository;
import org.di.digital.repository.user.*;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.cases.RejectionReasonStatusRepository;
import org.di.digital.repository.LogRepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
//import org.di.digital.repository.review.CaseReportRepository;
import org.di.digital.service.impl.report.CaseReportResolver;
import org.di.digital.repository.search.AppealSpecifications;
import org.di.digital.repository.search.CaseSpecifications;
import org.di.digital.repository.search.UserSpecifications;
import org.di.digital.repository.user.AppealRepository;
import org.di.digital.repository.user.RoleRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.admin.RegAdminService;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.service.core.MinioService;
import org.di.digital.service.export.interrogation.InterrogationExportService;
import org.di.digital.util.LocalizationHelper;
import org.di.digital.util.admin.RegionStatsService;
import org.di.digital.util.mapper.CaseMapper;
import org.di.digital.util.mapper.InterrogationMapper;
import org.di.digital.util.mapper.SupportMapper;
import org.di.digital.util.mapper.UserMapper;
import org.di.digital.util.requests.UserUtil;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.di.digital.util.mapper.UserMapper.AFM_REGION_ID;
import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegAdminServiceImpl implements RegAdminService {

    private final AppealRepository appealRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final LogRepository logRepository;
    private final RoleRepository roleRepository;
    private final ProfessionRepository professionRepository;
    private final UserMapper userMapper;
    private final CaseMapper caseMapper;
    private final InterrogationMapper interrogationMapper;
    private final SupportMapper supportMapper;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final InterrogationExportService interrogationExportService;
    private final UserUtil userUtil;
    private final CaseAccessService caseAccessService;
    private final RejectionReasonStatusRepository rejectionReasonStatusRepository;
//    private final CaseReportRepository caseReportRepository;
    private final CaseReportResolver caseReportResolver;
    private final MinioService minioService;
    private final RegionStatsService regionStatsService;
    private final LocalizationHelper localizationHelper;
    private static final String ZONAL_ROLE_NAME = "ZONAL";
    private static final String USER_ROLE_NAME = "USER";
    private static final String ADVANCED_ROLE_NAME = "ADVANCED_USER";
    private static final String ADMIN_ROLE_NAME = "ADMIN";
    private static final String REG_ADMIN_ROLE_NAME = "REG_ADMIN";
    private static final Set<Long> ADVANCED_PROFESSION_IDS = Set.of(3L, 4L);

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
        Specification<User> spec;
        if (userUtil.isRegAdminAfm(admin)) {
            // сотрудник АФМ: все пользователи по всем регионам
            spec = UserSpecifications.build(req);
        } else {
            List<Long> regionIds = userUtil.getOwnRegionIds(admin);
            spec = UserSpecifications.buildForRegions(regionIds, req);
        }

        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findAll(spec, pageable).map(userMapper::toProfile);



//        List<Region> adminRegions = userUtil.getAdminRegions(admin);
//
//        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();
//
//        Pageable pageable = PageRequest.of(page, size);
//        Specification<User> spec = UserSpecifications.buildForRegions(regionIds, req);
//        return userRepository.findAll(spec, pageable).map(userMapper::toProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public CasePageResponse getUserCases(Long adminId, Long userId, int page, int size, CaseSearchRequest req) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        if (admin.getRegion().getId() != null && admin.getRegion().getId() != AFM_REGION_ID) {
            userUtil.validateUserRegionAccess(admin, user);
        }

        Specification<Case> spec = CaseSpecifications.build(req)
                .and(CaseSpecifications.hasOwner(userId));

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size))
                .map(caseMapper::toListResponse);

        return caseMapper.build(spec, casePage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseUserResponse> getCaseUsers(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        return caseEntity.getUsers().stream()
                .map(user -> caseMapper.toUserResponse(user, caseEntity))
                .toList();
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

//        List<Long> regionIds = userUtil.getAdminRegions(admin).stream()
//                .map(Region::getId)
//                .toList();

        Specification<Case> spec;
        if (userUtil.isRegAdminAfm(admin)) {
            // сотрудник АФМ: все дела по всем регионам
            spec = CaseSpecifications.build(req);
        } else {
            List<Long> regionIds = userUtil.getOwnRegionIds(admin);
            spec = CaseSpecifications.buildForRegions(regionIds, req);
        }

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

        if (admin.getRegion().getId() != null && admin.getRegion().getId() != AFM_REGION_ID) {
            userUtil.validateRegionAccess(admin, caseEntity);
        }

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
    public RegionStatsDto getMyRegionStats(Long adminId, LocalDate from, LocalDate to) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        List<Region> adminRegions = userUtil.getAdminRegions(admin);

        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();

        long totalUsers = 0, totalCases = 0, totalInterrogations = 0, totalPages = 0, totalAudios = 0,
                totalQualifications = 0, totalIndictments = 0, totalAiMessages = 0,
                totalSelectedMessages = 0, totalReformulatedMessages = 0;

        for (Long id : regionIds) {
            AdminStatsDto s = regionStatsService.getStats(from, to, id);
            totalUsers += s.getTotalUsers();
            totalCases += s.getTotalCases();
            totalInterrogations += s.getTotalInterrogations();
            totalPages += s.getTotalPages();
            totalAudios += s.getTotalAudios();
            totalQualifications += s.getTotalQualifications();
            totalIndictments += s.getTotalIndictments();
            totalAiMessages += s.getTotalAiMessages();
            totalSelectedMessages += s.getTotalSelectedMessages();
            totalReformulatedMessages += s.getTotalReformulatedMessages();
        }

        Region single = adminRegions.size() == 1 ? adminRegions.get(0) : null;

        return RegionStatsDto.builder()
                .regionId(single != null ? single.getId() : null)
                .mapCode(single != null ? single.getMapCode() : null)
                .regionName(single != null ? localizationHelper.getLocalizedName(single, currentLang()) : null)
                .totalUsers(totalUsers)
                .activeUsers(userRepository.countByRegionIdInAndActiveTrue(regionIds))
                .totalCases(totalCases)
                .pendingAppeals(appealRepository.countByRegionIdInAndStatus(regionIds, AppealStatus.PENDING))
                .totalInterrogations(totalInterrogations)
                .totalPages(totalPages)
                .totalAudios(totalAudios)
                .totalQualifications(totalQualifications)
                .totalIndictments(totalIndictments)
                .totalAiMessages(totalAiMessages)
                .totalSelectedMessages(totalSelectedMessages)
                .totalReformulatedMessages(totalReformulatedMessages)
                .build();
    }

    @Override
    public List<RegionStatsDto> getRegionMapStats() {
        return regionStatsService.getRegionMapStats(currentLang());
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
            throw new IllegalStateException(MessageConstant.USER_IS_NOT_ACTIVE.format(currentLang(), newOwner.getEmail()));
        }
        userUtil.validateUserRegionAccess(admin, newOwner);

        caseAccessService.reassignOwner(caseEntity, newOwner, admin.getEmail());
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
    @Transactional
    public void assignZonalUserRole(Long adminId, String email) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

        userUtil.validateUserRegionAccess(admin, user);

        if (!user.isActive()) {
            throw new IllegalStateException(MessageConstant.USER_IS_NOT_ACTIVE.format(currentLang()));
        }
        ensureRoleCanBeChanged(user);
        Region region = user.getRegion();

        userRepository.findByRegionIdAndRoles_Name(region.getId(), ZONAL_ROLE_NAME).stream()
                .filter(previous -> !previous.getId().equals(user.getId()))
                .forEach(previous -> {
                    replaceRoles(previous, USER_ROLE_NAME);
                    detachFromRegions(previous, null);

                    log.info("User {} lost {} role in region {}: replaced by {} (admin {})",
                            previous.getEmail(), ZONAL_ROLE_NAME, region.getId(), email, adminId);
                });

        replaceRoles(user, ZONAL_ROLE_NAME);

        detachFromRegions(user, region.getId());

        if (region.getAdmins().stream().noneMatch(a -> a.getId().equals(user.getId()))) {
            region.getAdmins().add(user);
        }

        log.info("User {} assigned {} role by admin {}", email, ZONAL_ROLE_NAME, adminId);
    }
    @Override
    @Transactional
    public void assignAdvancedUserRole(Long adminId, String email, Long professionId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

        userUtil.validateUserRegionAccess(admin, user);

        if (!user.isActive()) {
            throw new IllegalStateException(MessageConstant.USER_IS_NOT_ACTIVE.format(currentLang()));
        }

        ensureRoleCanBeChanged(user);
        Profession profession = resolveAdvancedProfession(professionId);

        replaceRoles(user, ADVANCED_ROLE_NAME);
        user.setProfession(profession);

        Region region = user.getRegion();
        detachFromRegions(user, region != null ? region.getId() : null);

        if (region != null && region.getAdmins().stream().noneMatch(a -> a.getId().equals(user.getId()))) {
            region.getAdmins().add(user);
        }

        log.info("User {} assigned {} role with profession {} by admin {}",
                email, ADVANCED_ROLE_NAME, professionId, adminId);
    }

    @Override
    @Transactional
    public void assignUserRole(Long adminId, String email) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

        userUtil.validateUserRegionAccess(admin, user);
        ensureRoleCanBeChanged(user);

        replaceRoles(user, USER_ROLE_NAME);
        detachFromRegions(user, null);

        log.info("User {} assigned {} role by admin {}", email, USER_ROLE_NAME, adminId);
    }

    private Profession resolveAdvancedProfession(Long professionId) {
        if (professionId == null || !ADVANCED_PROFESSION_IDS.contains(professionId)) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OPERATION.localized(currentLang()));
        }
        return professionRepository.findById(professionId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.PROFESSION.localized(currentLang(), professionId.toString())));
    }

    private void ensureRoleCanBeChanged(User user) {
        boolean isProtected = user.getRoles().stream()
                .anyMatch(r -> ADMIN_ROLE_NAME.equals(r.getName()) || REG_ADMIN_ROLE_NAME.equals(r.getName()));
        if (isProtected) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OPERATION.localized(currentLang()));
        }
    }
    private void replaceRoles(User user, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.ROLE.localized(currentLang())));

        user.getRoles().clear();
        user.getRoles().add(role);
    }

    private void detachFromRegions(User user, Long keepRegionId) {
        regionRepository.findAllByAdmins_Id(user.getId()).stream()
                .filter(r -> keepRegionId == null || !r.getId().equals(keepRegionId))
                .forEach(r -> r.getAdmins().removeIf(a -> a.getId().equals(user.getId())));
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
    public Resource getMyRegionReport(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        if (admin.getRegion().getId() != null && admin.getRegion().getId() != AFM_REGION_ID) {
            userUtil.validateRegionAccess(admin, caseEntity);
        }

        return caseReportResolver.getOrGenerate(admin.getId(), caseEntity);

//        CaseReport report = caseReportRepository.findByCaseEntityNumber(caseEntity.getNumber())
//                .orElseThrow(() -> new NotFoundException(NotFoundMessage.REPORT.localized(currentLang(), caseId.toString())));
//
//        if (report.getStatus() != CaseFileStatusEnum.COMPLETED) {
//            throw new IllegalStateException(
//                    IllegalStateMessage.INVALID_OUTPUT.localized(currentLang(), report.getStatus().getLabel()));
//        }
//        if (report.getReportFileUrl() == null || report.getReportFileUrl().isBlank()) {
//            throw new NotFoundException(NotFoundMessage.FILE.localized(currentLang()));
//        }
//
//        try (InputStream stream = minioService.downloadFile(report.getReportFileUrl())) {
//            return new ByteArrayResource(stream.readAllBytes());
//        } catch (IOException e) {
//            throw new IllegalStateException(IllegalStateMessage.INVALID_OUTPUT.localized(currentLang()));
//        }
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

    private UserSettingsLanguage currentLang() {
        return getCurrentLang();
    }

    @Override
    @Transactional
    public void addParticipant(Long adminId, Long caseId, Long userId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        User userToAdd = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        if (!userToAdd.isActive()) {
            throw new IllegalStateException(MessageConstant.USER_IS_NOT_ACTIVE.format(currentLang()));
        }
        userUtil.validateUserRegionAccess(admin, userToAdd);

        if (!caseEntity.hasUser(userToAdd)) {
            caseEntity.addUser(userToAdd);
            caseRepository.save(caseEntity);
            log.info("Админ {} добавил участника {} в дело {}", adminId, userId, caseId);
        }
        caseAccessService.grantInitialAccess(caseEntity, userToAdd, null);
    }

    @Override
    @Transactional
    public void removeParticipant(Long adminId, Long caseId, Long userId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));

        userUtil.validateRegionAccess(admin, caseEntity);

        User userToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), userId.toString())));

        if (caseEntity.isOwner(userToRemove)) {
            throw new IllegalStateException(IllegalStateMessage.INVALID_OPERATION.localized(currentLang()));
        }
        if (caseEntity.hasUser(userToRemove)) {
            caseEntity.removeUser(userToRemove);
            caseRepository.save(caseEntity);
            log.info("Админ {} удалил участника {} из дела {}", adminId, userId, caseId);
        }
        caseAccessService.revokeAll(caseId, userId);
    }

    @Override
    @Transactional
    public void assignAdvancedUserRole(Long adminId, String email) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), adminId.toString())));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));

        // пользователь должен быть из региона этого рег-админа
        userUtil.validateUserRegionAccess(admin, user);

        if (!user.isActive()) {
            throw new IllegalStateException(MessageConstant.USER_IS_NOT_ACTIVE.format(currentLang()));
        }

//        addRoleIfMissing(user, ADVANCED_ROLE_NAME);

        // как и у ADMIN: регион пользователя попадает в его responsibleRegions
        Region region = user.getRegion();
        if (region.getAdmins().stream().noneMatch(a -> a.getId().equals(user.getId()))) {
            region.getAdmins().add(user);
        }

        log.info("User {} assigned {} role by admin {}", email, ADVANCED_ROLE_NAME, adminId);
    }
}