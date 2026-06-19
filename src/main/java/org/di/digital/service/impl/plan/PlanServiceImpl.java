package org.di.digital.service.impl.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.plan.AddPlanActionRequest;
import org.di.digital.dto.request.plan.ManualStatusRequest;
import org.di.digital.dto.response.plan.*;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.*;
import org.di.digital.model.plan.PlanEditHistory;
import org.di.digital.model.plan.PlanNotification;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.plan.PlanEditHistoryRepository;
import org.di.digital.repository.plan.PlanNotificationRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.LogService;
import org.di.digital.service.plan.PlanService;
import org.di.digital.service.export.DocumentFormatterService;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.util.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.di.digital.util.requests.RequestBodyBuilder.planBody;
import static org.di.digital.util.requests.RequestUrlBuilder.planGeneratorUrl;
import static org.di.digital.util.requests.RequestUrlBuilder.planUpdateUrl;
import static org.di.digital.util.requests.RequestBodyBuilder.manualStatusBody;
import static org.di.digital.util.requests.RequestUrlBuilder.manualStatusUrl;
import static org.di.digital.util.requests.UserUtil.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;
    private final DocumentFormatterService documentFormatterService;
    private final NotificationService notificationService;
    private final LogService logService;
    private final PlanEditHistoryRepository planEditHistoryRepository;
    private final PlanNotificationRepository planNotificationRepository;

    private final Mapper mapper;

    @Value("${model.host}")
    private String planHost;

    @Value("${plan.port}")
    private String planPort;

    @Override
    public CasePlanResponse generatePlan(String caseNumber, String mode, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new IllegalStateException("Дело не найдено: " + caseNumber));

        if (!caseEntity.isAtLeastOneFileProcessed()) {
            String message = MessageConstant.NO_FILE_PROCESSED.format(caseNumber);
            log.warn(message);
            logService.log(
                    String.format("No file processed for plan request in case %s", caseNumber),
                    LogLevel.ERROR,
                    LogAction.NO_FILE_PROCESSED,
                    caseNumber,
                    email
            );
            throw new IllegalStateException(message);
        }

        if (mode.equals("initial") && (caseEntity.getPlanStatus() == PlanStatus.PENDING
                || caseEntity.getPlanStatus() == PlanStatus.APPROVED_L1
                || caseEntity.getPlanStatus() == PlanStatus.APPROVED_L2
                || caseEntity.getPlanStatus() == PlanStatus.APPROVED_L3)) {
            throw new IllegalStateException(
                    "Нельзя перегенерировать план при статусе: " + caseEntity.getPlanStatus().getDescription());
        }

        MultiValueMap<String, Object> body = planBody(caseNumber, mode);

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(planGeneratorUrl(planHost, planPort))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new IllegalStateException("AI вернул пустой ответ для плана дела: " + caseNumber);
        }
        Map<String, Object> planTitleInfo = (Map<String, Object>) response.get("plan_title_info");
        if (planTitleInfo != null) {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            planTitleInfo.put("год", today);

            String state = (String) planTitleInfo.get("статья_ук_рк");
            if (state != null && !state.isEmpty()) {
                planTitleInfo.put("статья_ук_рк",
                        Character.toLowerCase(state.charAt(0)) + state.substring(1));
            }
        }

        List<Map<String, Object>> actions = (List<Map<String, Object>>) response.get("actions");
        if (actions != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate today = LocalDate.now();
            String executorName = getExecutorName(caseEntity);

            actions.forEach(action -> {
                if (executorName != null) {
                    action.put("исполнитель", executorName);
                }

                Object daysStr = action.get("дней");
                if (daysStr != null) {
                    try {
                        int days = ((Number) daysStr).intValue();
                        String deadline = today.plusDays(days).format(formatter);
                        action.put("срок", deadline);
                    } catch (Exception e) {
                        log.warn("Не удалось пересчитать срок для действия {}: {}",
                                action.get("номер"), e.getMessage());
                    }
                }
            });
        }
        Map<String, Object> caseData = (Map<String, Object>) response.get("case_data");
        if (caseData != null) {
            String executorName = getExecutorName(caseEntity);
            if (executorName != null) {
                caseData.put("фио_следователя", executorName);
            }
        }
        if (caseEntity.getPlanGeneratedAt() == null) {
            caseEntity.setPlanGeneratedAt(LocalDateTime.now());
        }

        caseEntity.setPlanStatus(PlanStatus.DRAFT);
        caseEntity.setPlan(response);
        caseRepository.save(caseEntity);

        log.info("Plan generated for case: {}", caseNumber);

        logService.log(
                String.format("Plan generated by %s in case %s", email, caseNumber),
                LogLevel.INFO,
                LogAction.PLAN_GENERATED,
                caseNumber,
                email
        );

        return CasePlanResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .approvedBy(getApproverName(caseEntity))
                .reviewedBy(getReviewerName(caseEntity))
                .canWithdraw(canWithdraw(caseEntity.getPlanStatus()))
                .plan(enrichPlanWithStatus(caseEntity.getPlan()))
                .build();
    }
    private String getExecutorName(Case caseEntity) {
        User owner = caseEntity.getOwner();
        if (owner == null) return null;
        return owner.getSurname() + " " + owner.getName().charAt(0) + "." + owner.getFathername().charAt(0);
    }

    public Map<String, Object> enrichPlanWithStatus(Map<String, Object> plan) {
        if (plan == null) return null;

        Map<String, Object> result = new java.util.LinkedHashMap<>(plan);

        List<Map<String, Object>> actions = (List<Map<String, Object>>) result.get("actions");
        if (actions == null){
            log.info("I dont know where is the actions");
            return result;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> enrichedActions = actions.stream().map(action -> {
            Map<String, Object> enriched = new java.util.LinkedHashMap<>(action);
            String срокStr = (String) action.get("срок");
            if (срокStr != null) {
                try {
                    LocalDate deadline = LocalDate.parse(срокStr, formatter);
                    long daysUntil = ChronoUnit.DAYS.between(today, deadline);

                    String статус;
                    if (daysUntil <= 1) {
                        статус = "красный";
                    } else if (daysUntil <= 3) {
                        статус = "желтый";
                    } else {
                        статус = "серый";
                    }
                    enriched.put("цвет", статус);
                } catch (Exception e) {
                    log.warn("Не удалось разобрать срок '{}' для действия {}", срокStr, action.get("номер"));
                }
            }
            return enriched;
        }).toList();

        result.put("actions", enrichedActions);
        return result;
    }
    @Override
    public Resource downloadPlanAsWord(String caseNumber, String userEmail) {
        try {
            logService.log(
                    String.format("Downloading plan by %s user in case %s", userEmail, caseNumber),
                    LogLevel.INFO,
                    LogAction.PLAN_DOWNLOAD,
                    caseNumber,
                    userEmail
            );
            return new ByteArrayResource(
                    documentFormatterService.generatePlanDocument(getPlan(caseNumber, userEmail).getPlan())
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public CasePlanResponse getPlan(String caseNumber, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        if (isRegAdmin(user) && caseEntity.getPlanStatus() == PlanStatus.PENDING) {
            throw new AccessDeniedException("План ещё не согласован и недоступен для просмотра");
        }

        log.info("Returning plan for case: {}", caseNumber);
        return CasePlanResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .canWithdraw(canWithdraw(caseEntity.getPlanStatus()))
                .approvedBy(getApproverName(caseEntity))
                .reviewedBy(getReviewerName(caseEntity))
                .plan(enrichPlanWithStatus(caseEntity.getPlan()))
                .build();
    }

    @Override
    @Transactional
    public PlanSubmitResponse submitPlan(String caseNumber, String email) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        validateUserAccess(caseEntity, user);

        if (caseEntity.getPlan() == null) {
            throw new IllegalStateException("План отсутствует");
        }

        Long regionId = caseEntity.getOwner().getRegion().getId();
        Long administrationId = caseEntity.getOwner().getAdministration().getId();

        boolean isAdvancedUser = user.hasRole("ADVANCED_USER");

        if (isAdvancedUser) {
            caseEntity.setPlanStatus(PlanStatus.APPROVED_L1);
            caseEntity.setPlanSubmittedAt(LocalDateTime.now());
            caseEntity.setPlanAgreedAt(LocalDateTime.now());
            caseEntity.setPlanReviewedBy(user);
            caseEntity.setPlanReviewedAt(LocalDateTime.now());
            caseEntity.setPlanReviewComment(null);
            caseRepository.save(caseEntity);

            userRepository
                    .findActiveByProfessionIdAndRegionIdAndAdministrationId(
                            ApprovalLevel.LEVEL_FINAL.getProfessionId(), regionId, administrationId)
                    .forEach(a -> notificationService.notifyApproverPlanAwaiting(
                            caseEntity, a, ApprovalLevel.LEVEL_FINAL.getLevel()));

        } else {
            caseEntity.setPlanStatus(PlanStatus.PENDING);
            caseEntity.setPlanSubmittedAt(LocalDateTime.now());
            caseEntity.setPlanReviewComment(null);
            caseEntity.setPlanReviewedBy(null);
            caseEntity.setPlanReviewedAt(null);
            caseRepository.save(caseEntity);

            List.of(ApprovalLevel.LEVEL_1_ZAM, ApprovalLevel.LEVEL_1_RUK)
                    .forEach(level ->
                            userRepository.findActiveByProfessionIdAndRegionIdAndAdministrationId(
                                            level.getProfessionId(), regionId, administrationId)
                                    .forEach(a -> notificationService.notifyApproverPlanAwaiting(
                                            caseEntity, a, level.getLevel())));
        }

        logService.log(
                String.format("Plan submitted by %s in case %s", email, caseNumber),
                LogLevel.INFO, LogAction.PLAN_SUBMITTED, caseNumber, email
        );

        return PlanSubmitResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .planSubmittedAt(caseEntity.getPlanSubmittedAt())
                .canWithdraw(canWithdraw(caseEntity.getPlanStatus()))
                .build();
    }
    @Override
    public List<ManagementPendingPlanDto> getManagementPendingPlans(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        if (user.getAdministration() == null) {
            throw new IllegalStateException("У пользователя не назначено управление");
        }
        List<Case> cases;
        if (user.hasRole("ADVANCED_USER")) {
            cases = caseRepository.findByOwnerAdministrationId(user.getAdministration().getId());
        } else {
            cases = caseRepository.findByPlanStatusInAndOwnerAdministrationId(
                    List.of(PlanStatus.APPROVED_L1, PlanStatus.APPROVED_L2, PlanStatus.APPROVED_L3),
                    user.getAdministration().getId());
        }

        return cases.stream()
                .sorted(Comparator.comparingInt(c -> pendingPriority(c.getPlanStatus())))
                .map(c -> mapper.toManagementPendingPlanDto(c, enrichPlanWithStatus(c.getPlan())))
                .toList();
    }

    private int pendingPriority(PlanStatus status) {
        if (status == null) {
            return Integer.MAX_VALUE;
        }
        return switch (status) {
            case PENDING -> 0;
            case APPROVED_L1, APPROVED_L2 -> 1;
            default -> 2;
        };
    }

    @Override
    @Transactional
    public CasePlanResponse updatePlanField(String caseNumber, String email,
                                            int actionNumber, String key, Object value) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        if ("номер".equals(key)) {
            throw new IllegalArgumentException("Нельзя изменить номер действия");
        }

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) {
            throw new IllegalStateException("План отсутствует");
        }

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) {
            throw new IllegalStateException("Список действий отсутствует");
        }

        Map<String, Object> action = actions.stream()
                .filter(a -> ((Number) a.get("номер")).intValue() == actionNumber)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Действие №" + actionNumber + " не найдено"));

        String oldValue = action.get(key) != null ? action.get(key).toString() : null;

        if ("срок".equals(key) && value instanceof String) {
            value = normalizeDeadlineFormat((String) value);
        }

        action.put(key, value);
        plan.put("actions", actions);
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);

        log.info("Case {} — action #{} field '{}' updated by {}",
                caseNumber, actionNumber, key, email);

        planEditHistoryRepository.save(PlanEditHistory.builder()
                .caseEntity(caseEntity)
                .editor(user)
                .actionNumber(actionNumber)
                .fieldKey(key)
                .oldValue(oldValue)
                .newValue(value != null ? value.toString() : null)
                .editedAt(LocalDateTime.now())
                .build());

        return CasePlanResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .canWithdraw(canWithdraw(caseEntity.getPlanStatus()))
                .approvedBy(getApproverName(caseEntity))
                .reviewedBy(getReviewerName(caseEntity))
                .plan(enrichPlanWithStatus(caseEntity.getPlan()))
                .build();
    }

    private static final DateTimeFormatter DATE_FORMAT_RU = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_FORMAT_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String normalizeDeadlineFormat(String value) {
        try {
            LocalDate.parse(value, DATE_FORMAT_RU);
            return value;
        } catch (Exception ignored) {
            try {
                LocalDate parsed = LocalDate.parse(value, DATE_FORMAT_ISO);
                return parsed.format(DATE_FORMAT_RU);
            } catch (Exception e) {
                log.warn("Не удалось нормализовать дату '{}', сохраняю как есть", value);
                return value;
            }
        }
    }
    @Override
    @Transactional
    public CasePlanResponse addAction(String caseNumber, String email, AddPlanActionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) throw new IllegalStateException("План отсутствует");

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) throw new IllegalStateException("Список действий отсутствует");

        Map<String, Object> newAction = new LinkedHashMap<>();
        newAction.put("номер", 0); // временно, перенумеруем ниже
        newAction.put("действие", request.getAction());
        newAction.put("цель", request.getGoal());
        newAction.put("исполнитель", request.getExecutor());
        newAction.put("направление", request.getDirection());

        if (request.getDays() != null) {
            newAction.put("дней", request.getDays());
        }

        String срок = request.getDeadline();
        if (срок == null && request.getDays() != null) {
            срок = LocalDate.now().plusDays(request.getDays())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
        if (срок != null) {
            newAction.put("срок", срок);
        }

        if (request.getInsertAfter() != null) {
            int insertIndex = -1;

            if (request.getInsertAfter() == 0) {
                insertIndex = 0;
            } else {
                for (int i = 0; i < actions.size(); i++) {
                    if (((Number) actions.get(i).get("номер")).intValue() == request.getInsertAfter()) {
                        insertIndex = i + 1;
                        break;
                    }
                }
            }

            if (insertIndex == -1) {
                throw new RuntimeException("Действие №" + request.getInsertAfter() + " не найдено");
            }
            actions.add(insertIndex, newAction);
        } else {
            actions.add(newAction);
        }

        // Перенумеруем все
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).put("номер", i + 1);
        }

        plan.put("actions", actions);
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);
        syncPlanToAi(caseNumber, plan);

        log.info("Case {} — action added by {}", caseNumber, email);

        return CasePlanResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .canWithdraw(canWithdraw(caseEntity.getPlanStatus()))
                .approvedBy(getApproverName(caseEntity))
                .reviewedBy(getReviewerName(caseEntity))
                .plan(enrichPlanWithStatus(caseEntity.getPlan()))
                .build();
    }

    @Override
    @Transactional
    public CasePlanResponse deleteAction(String caseNumber, String email, int actionNumber) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

       // validateEditAccess(user, caseEntity.getPlanStatus());

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) throw new IllegalStateException("План отсутствует");

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) throw new IllegalStateException("Список действий отсутствует");

        boolean removed = actions.removeIf(
                a -> ((Number) a.get("номер")).intValue() == actionNumber
        );

        if (!removed) {
            throw new RuntimeException("Действие №" + actionNumber + " не найдено");
        }

        // Перенумеруем
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).put("номер", i + 1);
        }

        plan.put("actions", actions);
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);
        syncPlanToAi(caseNumber, plan);

        log.info("Case {} — action #{} deleted by {}", caseNumber, actionNumber, email);

        return CasePlanResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .canWithdraw(canWithdraw(caseEntity.getPlanStatus()))
                .approvedBy(getApproverName(caseEntity))
                .reviewedBy(getReviewerName(caseEntity))
                .plan(enrichPlanWithStatus(caseEntity.getPlan()))
                .build();
    }



    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public ManualStatusResponse updateActionStatus(String caseNumber, String email, ManualStatusRequest request) {
        ActionStatus newActionStatus = ActionStatus.fromValue(request.getNewStatus());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + email));

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException("Дело не найдено: " + caseNumber));

        if (caseEntity.getPlanStatus() != PlanStatus.APPROVED_L3) {
            throw new IllegalStateException("Изменение статуса действий доступно только для утверждённого плана");
        }

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) throw new IllegalStateException("План отсутствует");

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) throw new IllegalStateException("Список действий отсутствует");

        Map<String, Object> action = actions.stream()
                .filter(a -> ((Number) a.get("номер")).intValue() == request.getActionNumber())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Действие №" + request.getActionNumber() + " не найдено"));

        boolean isAdvancedUser = user.hasRole("ADVANCED_USER");
        boolean isRegAdmin = user.hasRole("REG_ADMIN");

        Boolean locked = (Boolean) action.get("статус_заблокирован");
        if (Boolean.TRUE.equals(locked)) {
            throw new AccessDeniedException("Статус действия заблокирован для изменения");
        }

        String role = isRegAdmin || isAdvancedUser ? "manager" : "investigator";

        Map<String, Object> requestBody = manualStatusBody(
                request.getActionNumber(),
                newActionStatus.getValue(),
                role,
                request.getComment()
        );

        Map<String, Object> aiResponse;
        try {
            aiResponse = webClientBuilder.build()
                    .post()
                    .uri(manualStatusUrl(planHost, planPort, caseNumber))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Failed to call manual_status on AI for case {}: {}", caseNumber, e.getMessage(), e);
            throw new IllegalStateException("Не удалось обновить статус через AI-сервис для дела " + caseNumber, e);
        }

        if (aiResponse == null || !Boolean.TRUE.equals(aiResponse.get("success"))) {
            throw new IllegalStateException("AI не подтвердил изменение статуса для дела: " + caseNumber);
        }

        String oldStatus = String.valueOf(aiResponse.get("old_status"));
        String newStatus = String.valueOf(aiResponse.get("new_status"));
        boolean newLocked = Boolean.TRUE.equals(aiResponse.get("locked"));

        action.put("статус", newStatus);
        action.put("статус_заблокирован", newLocked);

        List<Map<String, Object>> history = (List<Map<String, Object>>) action.get("история_статусов");
        if (history == null) {
            history = new java.util.ArrayList<>();
        }

        Map<String, Object> historyEntry = new java.util.LinkedHashMap<>();
        historyEntry.put("статус", newStatus);
        historyEntry.put("заблокирован", newLocked);
        historyEntry.put("роль", role);
        historyEntry.put("дата", LocalDateTime.now().toString());
        if (request.getComment() != null && !request.getComment().isBlank()) {
            historyEntry.put("комментарий", request.getComment());
        }
        history.add(historyEntry);
        action.put("история_статусов", history);

        plan.put("actions", actions);
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);

        log.info("Case {} — action #{} status changed from '{}' to '{}' by {}",
                caseNumber, request.getActionNumber(), oldStatus, newStatus, email);

        return ManualStatusResponse.builder()
                .success(true)
                .caseNumber(caseNumber)
                .actionNumber(request.getActionNumber())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .locked(newLocked)
                .message(String.format("Статус пункта №%d изменён на '%s'",
                        request.getActionNumber(), newStatus))
                .historyStatuses(history)
                .build();
    }
    @Override
    public List<PlanEditHistoryDto> getEditHistory(String caseNumber, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        return planEditHistoryRepository
                .findByCaseEntityIdOrderByEditedAtDesc(caseEntity.getId())
                .stream()
                .map(mapper::toPlanEditHistoryDto)
                .toList();
    }
    public boolean canWithdraw(PlanStatus status) {
        return status == PlanStatus.PENDING
                || status == PlanStatus.APPROVED_L1
                || status == PlanStatus.APPROVED_L2;
    }

    @Override
    public List<PlanNotification> getMyNotifications(String email) {
        return planNotificationRepository.findTop4ByUserEmailOrderByCreatedAtDesc(email);
    }

    @Override
    public void markAllRead(String email) {
        List<PlanNotification> notifications = planNotificationRepository
                .findTop4ByUserEmailOrderByCreatedAtDesc(email);
        notifications.forEach(n -> n.setRead(true));
        planNotificationRepository.saveAll(notifications);
    }

    @Override
    public void markOneRead(Long id, String email) {
        PlanNotification notification = planNotificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Уведомление не найдено"));
        if (!notification.getUserEmail().equals(email)) {
            throw new AccessDeniedException("Нет доступа");
        }
        notification.setRead(true);
        planNotificationRepository.save(notification);
    }

    @Override
    public long getUnreadCount(String email) {
        return planNotificationRepository.countByUserEmailAndIsReadFalse(email);
    }
    private void validateEditAccess(User user, PlanStatus status) {
        boolean isAdvancedUser = user.hasRole("ADVANCED_USER");
        boolean isRegAdmin = user.hasRole("REG_ADMIN");

        if (isRegAdmin) {
            if (status != PlanStatus.APPROVED_L1 && status != PlanStatus.APPROVED_L2) {
                throw new IllegalStateException("Редактирование недоступно при статусе: " + status.getDescription());
            }
        } else if (isAdvancedUser) {
            if (status != PlanStatus.PENDING) {
                throw new IllegalStateException("Редактирование недоступно при статусе: " + status.getDescription());
            }
        } else {
            if (status != PlanStatus.DRAFT) {
                throw new IllegalStateException("Редактирование недоступно при статусе: " + status.getDescription());
            }
        }
    }
    private void syncPlanToAi(String caseNumber, Map<String, Object> plan) {
        try {
            webClientBuilder.build()
                    .put()
                    .uri(planUpdateUrl(planHost, planPort, caseNumber))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(plan)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            log.info("Plan synced to AI for case {}", caseNumber);
        } catch (Exception e) {
            log.warn("Failed to sync plan to AI for case {}: {}", caseNumber, e.getMessage());
        }
    }

    public String getReviewerName(Case caseEntity) {
        User reviewer = caseEntity.getPlanReviewedBy();
        if (reviewer == null) return null;
        return reviewer.getSurname() + " " + reviewer.getName().charAt(0) + ".";
    }

    public String getApproverName(Case caseEntity) {
        User approver = caseEntity.getPlanApprovedBy();
        if (approver == null) return null;
        return approver.getSurname() + " " + approver.getName().charAt(0) + ".";
    }
}