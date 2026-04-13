package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.AppealDto;
import org.di.digital.dto.response.CaseResponse;
import org.di.digital.dto.response.UserProfile;
import org.di.digital.model.Appeal;
import org.di.digital.model.Case;
import org.di.digital.model.User;
import org.di.digital.model.enums.AppealStatus;
import org.di.digital.repository.AppealRepository;
import org.di.digital.repository.CaseRepository;
import org.di.digital.repository.UserRepository;
import org.di.digital.service.RegAdminService;
import org.di.digital.util.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegAdminServiceImpl implements RegAdminService {

    private final AppealRepository appealRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final Mapper mapper;

    @Override
    public Page<AppealDto> getMyRegionAppeals(Long adminId, int page, int size) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new RuntimeException("Admin has no region assigned");
        }

        Pageable pageable = PageRequest.of(page, size);
        return appealRepository.findByRegionId(admin.getRegion().getId(), pageable)
                .map(mapper::toAppealDto);
    }

    @Override
    public Page<UserProfile> getMyRegionUsers(Long adminId, int page, int size) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new RuntimeException("Admin has no region assigned");
        }

        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findByRegionId(admin.getRegion().getId(), pageable)
                .map(mapper::mapToUserProfileResponse);
    }


    @Override
    public Page<CaseResponse> getUserCases(Long adminId, Long userId, int page, int size) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (admin.getRegion() == null || !admin.getRegion().getId().equals(user.getRegion().getId())) {
            throw new RuntimeException("User is not in your region");
        }

        Pageable pageable = PageRequest.of(page, size);
        return caseRepository.findByUserId(userId, pageable)
                .map(mapper::mapToCaseResponse);
    }
    @Override
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
    public Page<CaseResponse> getMyRegionCases(Long adminId, int page, int size) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new RuntimeException("Admin has no region assigned");
        }

        Pageable pageable = PageRequest.of(page, size);
        return caseRepository.findByOwnerRegionId(admin.getRegion().getId(), pageable)
                .map(mapper::mapToCaseResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CaseResponse getMyRegionCaseDetail(Long adminId, Long caseId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRegion() == null) {
            throw new RuntimeException("Admin has no region assigned");
        }

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        if (caseEntity.getOwner() == null ||
                caseEntity.getOwner().getRegion() == null ||
                !caseEntity.getOwner().getRegion().getId().equals(admin.getRegion().getId())) {
            throw new AccessDeniedException("This case does not belong to your region");
        }

        return mapper.mapToCaseResponse(caseEntity);
    }

}
