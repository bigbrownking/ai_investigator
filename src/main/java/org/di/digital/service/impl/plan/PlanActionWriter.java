package org.di.digital.service.impl.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.request.plan.AddPlanActionRequest;
import org.di.digital.dto.request.plan.ManualStatusRequest;
import org.di.digital.dto.response.plan.CasePlanResponse;
import org.di.digital.dto.response.plan.ManualStatusResponse;
import org.di.digital.exception.NotFoundException;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.ActionStatus;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanActionWriter {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final PlanResponseAssembler assembler;

    private static final DateTimeFormatter RU = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Транзакция: добавить действие, перенумеровать, сохранить, собрать ответ.
     * Возвращает и DTO, и обновлённый plan — plan нужен для syncPlanToAi ВНЕ транзакции.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public PlanActionResult persistAddAction(String caseNumber, String email, AddPlanActionRequest request) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) throw new IllegalStateException("План отсутствует");

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) throw new IllegalStateException("Список действий отсутствует");

        Map<String, Object> newAction = new LinkedHashMap<>();
        newAction.put("номер", 0);
        newAction.put("действие", request.getAction());
        newAction.put("цель", request.getGoal());
        newAction.put("исполнитель", request.getExecutor());
        newAction.put("направление", request.getDirection());
        if (request.getDays() != null) newAction.put("дней", request.getDays());

        String срок = request.getDeadline();
        if (срок == null && request.getDays() != null) {
            срок = LocalDate.now().plusDays(request.getDays()).format(RU);
        }
        if (срок != null) newAction.put("срок", срок);

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

        renumber(actions);

        plan.put("actions", actions);
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);

        log.info("Case {} — action added by {}", caseNumber, email);

        CasePlanResponse response = assembler.build(caseEntity);
        return new PlanActionResult(response, plan);
    }

    /**
     * Транзакция: удалить действие, перенумеровать, сохранить, собрать ответ.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public PlanActionResult persistDeleteAction(String caseNumber, String email, int actionNumber) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new RuntimeException("Дело не найдено: " + caseNumber));

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) throw new IllegalStateException("План отсутствует");

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) throw new IllegalStateException("Список действий отсутствует");

        boolean removed = actions.removeIf(a -> ((Number) a.get("номер")).intValue() == actionNumber);
        if (!removed) {
            throw new RuntimeException("Действие №" + actionNumber + " не найдено");
        }

        renumber(actions);

        plan.put("actions", actions);
        caseEntity.setPlan(plan);
        caseRepository.save(caseEntity);

        log.info("Case {} — action #{} deleted by {}", caseNumber, actionNumber, email);

        CasePlanResponse response = assembler.build(caseEntity);
        return new PlanActionResult(response, plan);
    }

    private void renumber(List<Map<String, Object>> actions) {
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).put("номер", i + 1);
        }
    }
    // ===== updateActionStatus: фаза 1 (валидация + подготовка тела запроса) =====
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public ActionStatusPrep prepareActionStatus(String caseNumber, String email,
                                                ManualStatusRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + email));
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException("Дело не найдено: " + caseNumber));

        if (caseEntity.getPlanStatus() != PlanStatus.APPROVED_L3) {
            throw new IllegalStateException(
                    "Изменение статуса действий доступно только для утверждённого плана");
        }

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) throw new IllegalStateException("План отсутствует");

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) throw new IllegalStateException("Список действий отсутствует");

        Map<String, Object> action = actions.stream()
                .filter(a -> ((Number) a.get("номер")).intValue() == request.getActionNumber())
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Действие №" + request.getActionNumber() + " не найдено"));

        Boolean locked = (Boolean) action.get("статус_заблокирован");
        if (Boolean.TRUE.equals(locked)) {
            throw new IllegalStateException("Статус действия заблокирован для изменения");
        }

        boolean isManager = user.hasRole("ADVANCED_USER") || user.hasRole("REG_ADMIN");
        String role = isManager ? "manager" : "investigator";

        ActionStatus newActionStatus = ActionStatus.fromValue(request.getNewStatus());

        return new ActionStatusPrep(role, newActionStatus.getValue());
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public ManualStatusResponse applyActionStatus(String caseNumber, String email,
                                                  ManualStatusRequest request, String role,
                                                  Map<String, Object> aiResponse) {
        Case caseEntity = caseRepository.findByNumber(caseNumber)
                .orElseThrow(() -> new NotFoundException("Дело не найдено: " + caseNumber));

        Map<String, Object> plan = caseEntity.getPlan();
        if (plan == null) throw new IllegalStateException("План отсутствует");

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) throw new IllegalStateException("Список действий отсутствует");

        Map<String, Object> action = actions.stream()
                .filter(a -> ((Number) a.get("номер")).intValue() == request.getActionNumber())
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Действие №" + request.getActionNumber() + " не найдено"));

        String oldStatus = String.valueOf(aiResponse.get("old_status"));
        String newStatus = String.valueOf(aiResponse.get("new_status"));
        boolean newLocked = Boolean.TRUE.equals(aiResponse.get("locked"));

        action.put("статус", newStatus);
        action.put("статус_заблокирован", newLocked);

        List<Map<String, Object>> history = (List<Map<String, Object>>) action.get("история_статусов");
        if (history == null) history = new ArrayList<>();

        Map<String, Object> historyEntry = new LinkedHashMap<>();
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

    public record ActionStatusPrep(String role, String newStatusValue) {}

    /** response — готовый DTO; plan — для syncPlanToAi вне транзакции. */
    public record PlanActionResult(CasePlanResponse response, Map<String, Object> plan) {}
}