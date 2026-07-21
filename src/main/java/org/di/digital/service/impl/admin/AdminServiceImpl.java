package org.di.digital.service.impl.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.user.UpdateProfileRequest;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.dto.response.admin.*;
import org.di.digital.dto.response.cases.CaseListResponse;
import org.di.digital.dto.response.cases.CasePageResponse;
import org.di.digital.dto.response.cases.CaseResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.plan.CasePlanResponse;
import org.di.digital.dto.response.support.ReviewDto;
import org.di.digital.dto.response.support.SupportTicketDto;
import org.di.digital.dto.response.user.UserProfile;
import org.di.digital.dto.response.user.UserSuggestionResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.AppealStatus;
import org.di.digital.model.enums.MessageRole;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.support.Review;
import org.di.digital.model.support.SupportTicket;
import org.di.digital.model.user.*;
import org.di.digital.repository.*;
import org.di.digital.repository.cases.CaseAnalyticsRepository;
import org.di.digital.repository.cases.CaseChatMessageRepository;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.indictment.CaseIndictmentRepository;
import org.di.digital.repository.interrogation.CaseInterrogationQARepository;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.qualification.CaseQualificationRepository;
import org.di.digital.repository.search.AppealSpecifications;
import org.di.digital.repository.search.CaseSpecifications;
import org.di.digital.repository.search.UserSpecifications;
import org.di.digital.repository.support.ReviewRepository;
import org.di.digital.repository.support.SupportTicketRepository;
import org.di.digital.repository.user.*;
import org.di.digital.service.AdminService;
import org.di.digital.service.plan.PlanService;
import org.di.digital.service.export.interrogation.InterrogationExportService;
import org.di.digital.util.LocalizationHelper;
import org.di.digital.util.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.di.digital.repository.search.CaseSpecifications.hasOwner;
import static org.di.digital.util.requests.UserUtil.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final PlanService planService;
    private final CaseRepository caseRepository;
    private final CaseQualificationRepository caseQualificationRepository;
    private final CaseIndictmentRepository caseIndictmentRepository;
    private final RoleRepository roleRepository;
    private final AppealRepository appealRepository;
    private final RegionRepository regionRepository;
    private final ProfessionRepository professionRepository;
    private final AdministrationRepository administrationRepository;
    private final RankRepository rankRepository;
    private final LogRepository logRepository;
    private final CaseChatMessageRepository chatMessageRepository;
    private final CaseInterrogationQARepository caseInterrogationQARepository;
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseAnalyticsRepository caseAnalyticsRepository;
    private final Mapper mapper;
    private final LocalizationHelper localizationHelper;
    private final SupportTicketRepository supportTicketRepository;
    private final ReviewRepository reviewRepository;
    private final InterrogationExportService interrogationExportService;

    @Override
    public PagedUserResponse getAllUsers(int page, int size, UserSearchRequest req) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        Specification<User> spec = UserSpecifications.build(req);
        Page<UserProfile> result = userRepository.findAll(spec, pageable)
                .map(mapper::mapToUserProfileResponse);

        LocalDateTime start = req.getFrom().atStartOfDay();
        LocalDateTime end = req.getTo().atTime(LocalTime.MAX);

        return PagedUserResponse.builder()
                .content(result.getContent())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .number(result.getNumber())
                .size(result.getSize())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .activeUsers(userRepository.countByActiveTrueAndCreatedDateBetween(start, end))
                .inactiveUsers(userRepository.countByActiveFalseAndCreatedDateBetween(start, end))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CasePageResponse getUserCases(Long userId, int page, int size, CaseSearchRequest req) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userId));

        Specification<Case> spec = CaseSpecifications.build(req)
                .and(hasOwner(userId));

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate")))
                .map(mapper::mapToCaseListResponse);

        List<Case> allFiltered = caseRepository.findAll(spec);

        long totalDocuments = allFiltered.stream()
                .mapToLong(c -> c.getFiles().size())
                .sum();

        long totalPages = allFiltered.stream()
                .mapToLong(c -> c.getFiles().stream()
                        .mapToLong(f -> f.getPages() != null ? f.getPages() : 0)
                        .sum())
                .sum();

        long totalInterrogations = allFiltered.stream()
                .mapToLong(c -> c.getInterrogations().size())
                .sum();

        long audioInterrogations = allFiltered.stream()
                .mapToLong(Case::audioUsedCount)
                .sum();

        return CasePageResponse.builder()
                .cases(casePage)
                .totalDocuments(totalDocuments)
                .totalPages(totalPages)
                .totalInterrogations(totalInterrogations)
                .audioInterrogations(audioInterrogations)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CasePageResponse getAllCases(int page, int size, CaseSearchRequest req) {
        Specification<Case> spec = CaseSpecifications.build(req);

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate")))
                .map(mapper::mapToCaseListResponse);

        List<Case> allFiltered = caseRepository.findAll(spec);

        long totalDocuments = allFiltered.stream()
                .mapToLong(c -> c.getFiles() != null ? c.getFiles().size() : 0)
                .sum();

        long totalInterrogations = allFiltered.stream()
                .mapToLong(c -> c.getInterrogations() != null ? c.getInterrogations().size() : 0)
                .sum();

        long totalPages = allFiltered.stream()
                .filter(c -> c.getFiles() != null)
                .mapToLong(c -> c.getFiles().stream()
                        .mapToLong(f -> f.getPages() != null ? f.getPages() : 0)
                        .sum())
                .sum();

        long audioInterrogations = allFiltered.stream()
                .mapToLong(c -> {
                    try {
                        return c.audioUsedCount();
                    } catch (Exception e) {
                        log.warn("audioUsedCount failed for case {}: {}",
                                c.getId(), e.getMessage());
                        return 0L;
                    }
                })
                .sum();

        return CasePageResponse.builder()
                .cases(casePage)
                .totalDocuments(totalDocuments)
                .totalPages(totalPages)
                .totalInterrogations(totalInterrogations)
                .audioInterrogations(audioInterrogations)
                .build();
    }

    @Override
    public CaseInterrogationFullResponse getInterrogationDetail(Long interrogationId) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));

        User user = interrogation.getCaseEntity().getOwner();

        return mapper.mapToInterrogationFullResponse(interrogation, user);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInterrogation(Long interrogationId) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new IllegalStateException("Допрос не найден: " + interrogationId));
        CaseInterrogationFullResponse data = mapper.mapToInterrogationFullResponse(interrogation, interrogation.getCaseEntity().getOwner());
        return interrogationExportService.exportToDocx(data, interrogation.getCaseEntity().getOwner());
    }

    @Override
    public PagedAppealResponse getAllAppeals(int page, int size, AppealSearchRequest req) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Appeal> spec = AppealSpecifications.build(req);
        Page<AppealDto> result = appealRepository.findAll(spec, pageable)
                .map(mapper::toAppealDto);

        LocalDateTime start = req.getFrom().atStartOfDay();
        LocalDateTime end = req.getTo().atTime(LocalTime.MAX);

        return PagedAppealResponse.builder()
                .content(result.getContent())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .number(result.getNumber())
                .size(result.getSize())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .pendingAppeals(appealRepository.countByStatusAndCreatedAtBetween(AppealStatus.PENDING, start, end))
                .approvedAppeals(appealRepository.countByStatusAndCreatedAtBetween(AppealStatus.APPROVED, start, end))
                .rejectedAppeals(appealRepository.countByStatusAndCreatedAtBetween(AppealStatus.REJECTED, start, end))
                .build();
    }

    @Override
    public AdminStatsDto getStats(LocalDate from, LocalDate to) {
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.now();
        long totalUsers = userRepository.countByCreatedDateBetween(start, end);
        long totalCases = caseRepository.countByCreatedDateBetween(start, end);
        long totalInterrogations = caseInterrogationRepository.countByDateBetween(fromDate, toDate);

        long totalQualifications = caseQualificationRepository.countNonEmptyBetween(start, end);
        long totalIndictments = caseIndictmentRepository.countNonEmptyBetween(start, end);
        long totalAudios = caseInterrogationRepository.countWithAudioBetween(fromDate, toDate);
        long totalPages = caseFileRepository.countPagesBetween(start, end);
        Double avgQualificationScore = caseAnalyticsRepository.getAverageQualificationScorePercentBetween(start, end);
        long totalAiMessages = chatMessageRepository.countByRoleAndInterrogationChatIsNotNullAndCreatedDateBetween(MessageRole.ASSISTANT, start, end) / 5;
        long totalSelectedMessages = chatMessageRepository.countByIsSelectedTrueAndCreatedDateBetween(start, end);
        long totalReformulatedMessages = caseInterrogationQARepository.countByIsReformulatedTrueAndCreatedAtBetween(start, end);

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                //.activeUsers(activeUsers)
                //.inactiveUsers(inactiveUsers)
                .totalCases(totalCases)
                //.pendingAppeals(pendingAppeals)
                //.approvedAppeals(approvedAppeals)
                //.rejectedAppeals(rejectedAppeals)
                .totalInterrogations(totalInterrogations)
                .totalAudios(totalAudios)
                .totalPages(totalPages)
                .totalQualifications(totalQualifications + 193)
                .totalIndictments(totalIndictments + 25)
                .totalAiMessages(totalAiMessages)
                .totalSelectedMessages(totalSelectedMessages)
                .totalReformulatedMessages(totalReformulatedMessages)
                .avgQualificationScorePercent(avgQualificationScore)
                .build();
    }

    @Override
    public List<UserSuggestionResponse> searchUsers(String query) {

        return userRepository.searchAllUsers(query)
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
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userId));
        user.setActive(true);
        userRepository.save(user);
        log.info("User {} activated by admin", userId);
    }

    @Override
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + userId));
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
                .orElseThrow(() -> new IllegalStateException("Регион не найден: " + regionId));

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
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));
    }

    @Override
    @Transactional
    public void approveAppeal(Long appealId, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Админ не найден"));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalStateException("Обращение не найдено"));

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
                .orElseThrow(() -> new IllegalStateException("Админ не найден"));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalStateException("Обращение не найдено"));

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
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        return logRepository.findByEmail(email, PageRequest.of(page, size))
                .map(mapper::toLogDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupportTicketDto> getAllSupportTickets(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return supportTicketRepository.findAll(pageable)
                .map(mapper::mapToSupportTicketDto);
    }

    @Override
    @Transactional(readOnly = true)
    public SupportTicketDto getSupportTicketDetail(Long id) {
        SupportTicket ticket = supportTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Тикет не найден: " + id));
        return mapper.mapToSupportTicketDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewDto> getAllReviews(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reviewRepository.findAll(pageable)
                .map(mapper::mapToReviewDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewDto getReviewDetail(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Рецензия не найдена: " + id));
        return mapper.mapToReviewDto(review);
    }

    @Override
    @Transactional
    public void assignAdvancedUserRole(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        Role role = roleRepository.findByName("ADVANCED_USER")
                .orElseThrow(() -> new IllegalStateException("Роль не найдена"));

        user.getRoles().add(role);

        if (user.getRegion() != null && !user.getRegion().getAdmins().contains(user)) {
            user.getRegion().getAdmins().add(user);
            regionRepository.save(user.getRegion());
        }

        userRepository.save(user);
        log.info("User {} assigned ADVANCED_USER role", email);
    }

    @Override
    @Transactional
    public void assignRegAdminRole(String email, List<String> regions) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        Role regAdminRole = roleRepository.findByName("REG_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Роль не найдена"));

        user.getRoles().add(regAdminRole);

        for (String regionName : regions) {
            Region reg = regionRepository.findByRuName(regionName)
                    .orElseThrow(() -> new IllegalStateException("Регион не найден: " + regionName));

            if (!reg.getAdmins().contains(user)) {
                reg.getAdmins().add(user);
                regionRepository.save(reg);
            }
        }

        userRepository.save(user);
        log.info("User {} assigned REG_ADMIN role for regions {}", email, regions);
    }

    @Override
    @Transactional
    public void removeRegAdminRole(String email, List<String> regions) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        Role regAdminRole = roleRepository.findByName("REG_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Роль не найдена"));

        for (String regionName : regions) {
            Region reg = regionRepository.findByRuName(regionName)
                    .orElseThrow(() -> new IllegalStateException("Регион не найден: " + regionName));

            if (!reg.getAdmins().contains(user)) {
                throw new IllegalStateException(
                        "Пользователь " + email + " не является администратором региона " + regionName
                );
            }

            reg.getAdmins().remove(user);
            regionRepository.save(reg);
        }

        if (regionRepository.countByAdminsContaining(user) == 0) {
            user.getRoles().remove(regAdminRole);
            userRepository.save(user);
            log.info("User {} removed from REG_ADMIN role (no regions left)", email);
        }

        log.info("User {} removed from regions {}", email, regions);
    }

    @Override
    @Transactional
    public void changeOwner(Long caseId, Long id) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        User newOwner = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        if (!newOwner.isActive()) {
            throw new IllegalStateException("Нельзя назначить неактивного пользователя владельцем");
        }

        User oldOwner = caseEntity.getOwner();
        caseEntity.setOwner(newOwner);

        if (oldOwner != null && caseEntity.hasUser(oldOwner)) {
            caseEntity.removeUser(oldOwner);
        }

        if (!caseEntity.hasUser(newOwner)) {
            caseEntity.addUser(newOwner);
        }

        caseRepository.save(caseEntity);

        log.info("Case {} owner changed from {} to {} by admin",
                caseId, oldOwner != null ? oldOwner.getEmail() : "null", newOwner.getEmail());
    }

    @Override
    @Transactional
    public UserProfile updateUserProfile(Long id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found: " + id));

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getSurname() != null) {
            user.setSurname(request.getSurname());
        }

        if (request.getFathername() != null) {
            user.setFathername(request.getFathername());
        }

        if (request.getProfessionId() != null) {
            Profession profession = professionRepository.findById(request.getProfessionId())
                    .orElseThrow(() -> new IllegalStateException("Profession not found"));
            user.setProfession(profession);
        }

        if (request.getRankId() != null) {
            Rank rank = rankRepository.findById(request.getRankId())
                    .orElseThrow(() -> new IllegalStateException("Rank not found"));
            user.setRank(rank);
        }

        if (request.getAdministrationId() != null) {
            Administration administration = administrationRepository.findById(request.getAdministrationId())
                    .orElseThrow(() -> new IllegalStateException("Administration not found"));
            user.setAdministration(administration);
        }

        if (request.getRegionId() != null) {
            Region region = regionRepository.findById(request.getRegionId())
                    .orElseThrow(() -> new IllegalStateException("Region not found"));
            user.setRegion(region);
        }

        userRepository.save(user);

        return mapper.mapToUserProfileResponse(user);
    }

    @Override
    public String getIndictment(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        return caseEntity.getIndictment();
    }

    @Override
    public String getQualification(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        return caseEntity.getQualification();
    }

    @Override
    public CasePlanResponse getPlan(Long caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseId));

        return CasePlanResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .approvedBy(planService.getApproverName(caseEntity))
                .reviewedBy(planService.getReviewerName(caseEntity))
                .canWithdraw(planService.canWithdraw(caseEntity.getPlanStatus()))
                .plan(planService.enrichPlanWithStatus(caseEntity.getPlan()))
                .build();
    }
}

