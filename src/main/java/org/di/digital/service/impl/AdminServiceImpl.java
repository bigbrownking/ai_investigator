package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.search.AppealSearchRequest;
import org.di.digital.dto.request.search.CaseSearchRequest;
import org.di.digital.dto.request.search.UserSearchRequest;
import org.di.digital.dto.response.*;
import org.di.digital.model.*;
import org.di.digital.model.enums.AppealStatus;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.support.Review;
import org.di.digital.model.support.SupportTicket;
import org.di.digital.repository.*;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.repository.search.AppealSpecifications;
import org.di.digital.repository.search.CaseSpecifications;
import org.di.digital.repository.search.UserSpecifications;
import org.di.digital.repository.support.ReviewRepository;
import org.di.digital.repository.support.SupportTicketRepository;
import org.di.digital.service.AdminService;
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
    private final CaseInterrogationRepository caseInterrogationRepository;
    private final CaseFileRepository caseFileRepository;
    private final Mapper mapper;
    private final LocalizationHelper localizationHelper;
    private final SupportTicketRepository supportTicketRepository;
    private final ReviewRepository reviewRepository;
    private final InterrogationExportService interrogationExportService;

    @Override
    public Page<UserProfile> getAllUsers(int page, int size, UserSearchRequest req) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        Specification<User> spec = UserSpecifications.build(req);
        return userRepository.findAll(spec, pageable)
                .map(mapper::mapToUserProfileResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CasePageResponse getUserCases(Long userId, int page, int size, CaseSearchRequest req) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userId));

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
    public CasePageResponse getAllCases(int page, int size, CaseSearchRequest req) {
        Specification<Case> spec = CaseSpecifications.build(req);

        Page<CaseListResponse> casePage = caseRepository
                .findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate")))
                .map(mapper::mapToCaseListResponse);

        List<Case> allFiltered = caseRepository.findAll(spec);

        long totalDocuments = allFiltered.stream()
                .mapToLong(c -> c.getFiles().size())
                .sum();

        long totalInterrogations = allFiltered.stream()
                .mapToLong(c -> c.getInterrogations().size())
                .sum();

        long totalPages = allFiltered.stream()
                .mapToLong(c -> c.getFiles().stream()
                        .mapToLong(f -> f.getPages() != null ? f.getPages() : 0)
                        .sum())
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
    public CaseInterrogationFullResponse getInterrogationDetail(Long interrogationId) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        User user = interrogation.getCaseEntity().getOwner();

        return mapper.mapToInterrogationFullResponse(interrogation, user);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInterrogation(Long interrogationId) {
        CaseInterrogation interrogation = caseInterrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));
        CaseInterrogationFullResponse data = mapper.mapToInterrogationFullResponse(interrogation, interrogation.getCaseEntity().getOwner());
        return interrogationExportService.exportToDocx(data);
    }

    @Override
    public Page<AppealDto> getAllAppeals(int page, int size, AppealSearchRequest req) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Appeal> spec = AppealSpecifications.build(req);
        return appealRepository.findAll(spec, pageable)
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
        long totalInterrogations = caseInterrogationRepository.count();
        long totalQualifications = caseRepository.countByQualificationIsNotEmpty();
        long totalIndictments = caseRepository.countByIndictmentIsNotEmpty();
        long totalAudios = caseInterrogationRepository.countWithAudio();
        long totalPages = caseFileRepository.countPages();
        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .totalCases(totalCases)
                .pendingAppeals(pendingAppeals)
                .approvedAppeals(approvedAppeals)
                .rejectedAppeals(rejectedAppeals)
                .totalInterrogations(totalInterrogations)
                .totalAudios(totalAudios)
                .totalPages(totalPages)
                .totalQualifications(totalQualifications)
                .totalIndictments(totalIndictments)
                .build();
    }

    @Override
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userId));
        user.setActive(true);
        userRepository.save(user);
        log.info("User {} activated by admin", userId);
    }

    @Override
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userId));
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
                .orElseThrow(() -> new RuntimeException("Регион не найден: " + regionId));

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
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));
    }

    @Override
    public String getCaseQualification(Long caseId) {
        return caseRepository.findById(caseId)
                .map(Case::getQualification)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));
    }

    @Override
    public String getCaseIndictment(Long caseId) {
        return caseRepository.findById(caseId)
                .map(Case::getIndictment)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseId));
    }

    @Override
    @Transactional
    public void approveAppeal(Long appealId, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Админ не найден"));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("Обращение не найдено"));

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
                .orElseThrow(() -> new RuntimeException("Админ не найден"));

        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("Обращение не найдено"));

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
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
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
                .orElseThrow(() -> new RuntimeException("Тикет не найден: " + id));
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
                .orElseThrow(() -> new RuntimeException("Рецензия не найдена: " + id));
        return mapper.mapToReviewDto(review);
    }
}

