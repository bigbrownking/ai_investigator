package org.di.digital.service.impl.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.plan.PlanApprovalHistoryDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.plan.PlanApprovalHistory;
import org.di.digital.model.enums.ApprovalLevel;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.plan.PlanApprovalHistoryRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.plan.PlanApprovalService;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.util.Mapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanApprovalServiceImpl implements PlanApprovalService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final PlanApprovalHistoryRepository historyRepository;
    private final LogService logService;
    private final NotificationService notificationService;
    private final Mapper mapper;

    @Override
    @Transactional
    public void approvePlan(String email, String caseNumber) {
        User approver = loadUser(email);
        Case caseEntity = loadCase(caseNumber);
        ApprovalLevel lvl = resolveLevel(approver);

        if (!lvl.isFirstLevel()) {
            throw new AccessDeniedException("Для согласования используйте соответствующий эндпоинт");
        }

        validateCurrentStatus(caseEntity, PlanStatus.PENDING, lvl.getLevel());
        validateRegionAccess(approver, caseEntity);

        PlanStatus previous = caseEntity.getPlanStatus();

        caseEntity.setPlanStatus(PlanStatus.APPROVED_L1);
        caseEntity.setPlanReviewedBy(approver);
        caseEntity.setPlanReviewedAt(LocalDateTime.now());
        caseEntity.setPlanAgreedAt(LocalDateTime.now());
        caseEntity.setPlanReviewComment(null);
        caseRepository.save(caseEntity);

        saveHistory(caseEntity, approver, previous, PlanStatus.APPROVED_L1, lvl.getLevel(), null);
        notificationService.notifyPlanApproved(caseEntity, approver, lvl.getLevel());

        Long regionId = caseEntity.getOwner().getRegion().getId();
        Long administrationId = caseEntity.getOwner().getAdministration().getId();
        userRepository
                .findActiveByProfessionIdAndRegionIdAndAdministrationId(ApprovalLevel.LEVEL_FINAL.getProfessionId(), regionId, administrationId)
                .forEach(a -> notificationService.notifyApproverPlanAwaiting(
                        caseEntity, a, ApprovalLevel.LEVEL_FINAL.getLevel()));

        logService.log(
                String.format("Plan approved at level %d for case %s by %s",
                        lvl.getLevel(), caseEntity.getNumber(), approver.getEmail()),
                LogLevel.INFO, LogAction.PLAN_APPROVED, caseEntity.getNumber(), approver.getEmail()
        );
    }

    @Override
    @Transactional
    public void finalApprovePlan(String email, String caseNumber) {
        User approver = loadUser(email);
        Case caseEntity = loadCase(caseNumber);
        ApprovalLevel lvl = resolveLevel(approver);

        if (!lvl.isFinalLevel()) {
            throw new AccessDeniedException("Финальное утверждение доступно только зам. департамента");
        }

        validateFinalApproveStatus(caseEntity, lvl.getLevel());
        validateRegionAccess(approver, caseEntity);

        PlanStatus previous = caseEntity.getPlanStatus();

        caseEntity.setPlanStatus(PlanStatus.APPROVED_L3);
        caseEntity.setPlanReviewedAt(LocalDateTime.now());
        caseEntity.setPlanApprovedAt(LocalDateTime.now());
        caseEntity.setPlanReviewComment(null);
        caseEntity.setPlanApprovedBy(approver);
        caseRepository.save(caseEntity);

        saveHistory(caseEntity, approver, previous, PlanStatus.APPROVED_L3, lvl.getLevel(), null);
        notificationService.notifyPlanApproved(caseEntity, approver, lvl.getLevel());

        logService.log(
                String.format("Plan final approved for case %s by %s", caseNumber, email),
                LogLevel.INFO, LogAction.PLAN_APPROVED, caseNumber, email
        );
    }

    @Override
    @Transactional
    public void rejectPlan(String email, String caseNumber, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Комментарий обязателен при отклонении");
        }

        User approver = loadUser(email);
        Case caseEntity = loadCase(caseNumber);
        ApprovalLevel lvl = resolveLevel(approver);

        validateCurrentStatus(caseEntity, lvl.getRequiredStatus(), lvl.getLevel());
        validateRegionAccess(approver, caseEntity);

        PlanStatus previous = caseEntity.getPlanStatus();

        caseEntity.setPlanStatus(PlanStatus.REJECTED);
        caseEntity.setPlanReviewedBy(approver);
        caseEntity.setPlanReviewedAt(LocalDateTime.now());
        caseEntity.setPlanReviewComment(comment);
        caseEntity.setPlanAgreedAt(null);
        caseEntity.setPlanApprovedAt(null);
        caseRepository.save(caseEntity);

        saveHistory(caseEntity, approver, previous, PlanStatus.REJECTED, lvl.getLevel(), comment);
        notificationService.notifyPlanRejected(caseEntity, approver, lvl.getLevel(), comment);

        logService.log(
                String.format("Plan rejected at level %d for case %s by %s",
                        lvl.getLevel(), caseEntity.getNumber(), approver.getEmail()),
                LogLevel.INFO, LogAction.PLAN_REJECTED,
                caseEntity.getNumber(), approver.getEmail()
        );

        log.info("Case {} — plan rejected at level {} by user {}",
                caseNumber, lvl.getLevel(), email);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanApprovalHistoryDto> getHistory(String email, String caseNumber) {
        User requester = loadUser(email);
        Case caseEntity = loadCase(caseNumber);

        validateRegionAccess(requester, caseEntity);

        return historyRepository
                .findByCaseEntityIdOrderByReviewedAtDesc(caseEntity.getId())
                .stream()
                .map(mapper::toPlanApprovalHistoryDto)
                .toList();
    }

    @Override
    @Transactional
    public void withdrawPlan(String email, String caseNumber) {
        User user = loadUser(email);
        Case caseEntity = loadCase(caseNumber);

        if (!caseEntity.getOwner().getEmail().equals(email)) {
            throw new AccessDeniedException("Только следователь может отозвать план");
        }

        PlanStatus current = caseEntity.getPlanStatus();

        if (current == PlanStatus.APPROVED_L3) {
            throw new IllegalStateException("Утверждённый план нельзя отозвать");
        }
        if (current != PlanStatus.PENDING
                && current != PlanStatus.APPROVED_L1
                && current != PlanStatus.APPROVED_L2) {
            throw new IllegalStateException(
                    "Отзыв невозможен при статусе: " + current.getDescription());
        }

        caseEntity.setPlanStatus(PlanStatus.WITHDRAWN);
        caseEntity.setPlanReviewedBy(null);
        caseEntity.setPlanReviewedAt(null);
        caseEntity.setPlanReviewComment(null);
        caseEntity.setPlanAgreedAt(null);
        caseEntity.setPlanApprovedAt(null);
        caseEntity.setPlanApprovedBy(null);
        caseRepository.save(caseEntity);

        saveHistory(caseEntity, user, current, PlanStatus.WITHDRAWN, 0, null);

        logService.log(
                String.format("Plan withdrawn by %s for case %s", email, caseNumber),
                LogLevel.INFO, LogAction.PLAN_WITHDRAWN, caseNumber, email
        );

        log.info("Case {} — plan withdrawn by {}", caseNumber, email);
    }

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
    }

    private Case loadCase(String caseNumber) {
        return caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));
    }

    private ApprovalLevel resolveLevel(User approver) {
        if (approver.getProfession() == null) {
            throw new AccessDeniedException("У пользователя не назначена профессия");
        }
        return ApprovalLevel.fromProfession(approver.getProfession().getId());
    }

    private void validateCurrentStatus(Case c, PlanStatus required, int level) {
        if (c.getPlanStatus() != required) {
            throw new IllegalStateException(String.format(
                    "Для уровня %d требуется статус '%s', текущий статус: '%s'",
                    level, required.getDescription(), c.getPlanStatus().getDescription()
            ));
        }
    }
    private void validateFinalApproveStatus(Case c, int level) {
        PlanStatus status = c.getPlanStatus();
        if (status != PlanStatus.APPROVED_L1 && status != PlanStatus.APPROVED_L2) {
            throw new IllegalStateException(String.format(
                    "Для уровня %d требуется статус '%s' или '%s', текущий статус: '%s'",
                    level,
                    PlanStatus.APPROVED_L1.getDescription(),
                    PlanStatus.APPROVED_L2.getDescription(),
                    status.getDescription()
            ));
        }
    }

    private void validateRegionAccess(User approver, Case c) {
        if (approver.getRegion() == null) {
            throw new AccessDeniedException("У пользователя не назначен регион");
        }

        User owner = c.getOwner();
        if (owner == null || owner.getRegion() == null
                || !owner.getRegion().getId().equals(approver.getRegion().getId())) {
            throw new AccessDeniedException("Дело не принадлежит вашему региону");
        }
    }

    private void saveHistory(Case c, User reviewer,
                             PlanStatus from, PlanStatus to,
                             int level, String comment) {
        PlanApprovalHistory entry = PlanApprovalHistory.builder()
                .caseEntity(c)
                .reviewer(reviewer)
                .fromStatus(from)
                .toStatus(to)
                .approvalLevel(level)
                .comment(comment)
                .build();
        historyRepository.save(entry);
    }
}