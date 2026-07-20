package org.di.digital.service.impl.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.ApprovalLevel;
import org.di.digital.model.plan.CasePlan;
import org.di.digital.repository.plan.CasePlanRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.impl.core.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanActionNotifier {

    private final CasePlanRepository casePlanRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    @SuppressWarnings("unchecked")
    public void checkAndNotifyRedActions(CasePlan casePlan) {
        Case caseEntity = casePlan.getCaseEntity();

        Map<String, Object> plan = casePlan.getContent();
        if (plan == null) return;

        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        if (actions == null) return;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate today = LocalDate.now();

        Set<Integer> alreadyNotified = casePlan.getNotifiedRedActions() != null
                ? new HashSet<>(casePlan.getNotifiedRedActions())
                : new HashSet<>();

        boolean hasNew = false;

        for (Map<String, Object> action : actions) {
            String dateStr = (String) action.get("срок");
            Object numberObj = action.get("номер");
            if (dateStr == null || numberObj == null) continue;

            int number = ((Number) numberObj).intValue();

            try {
                LocalDate deadline = LocalDate.parse(dateStr, formatter);
                long daysUntil = ChronoUnit.DAYS.between(today, deadline);

                if (daysUntil <= 1 && !alreadyNotified.contains(number)) {
                    Long regionId = caseEntity.getOwner().getRegion().getId();
                    Long administrationId = caseEntity.getOwner().getAdministration().getId();

                    List.of(ApprovalLevel.LEVEL_1_ZAM, ApprovalLevel.LEVEL_1_RUK)
                            .forEach(level ->
                                    userRepository.findActiveByProfessionIdAndRegionIdAndAdministrationId(
                                                    level.getProfessionId(), regionId, administrationId)
                                            .forEach(supervisor ->
                                                    notificationService.notifyRedActionDeadline(
                                                            caseEntity, supervisor, action)));

                    alreadyNotified.add(number);
                    hasNew = true;
                }
            } catch (Exception e) {
                log.warn("Не удалось разобрать срок '{}' для действия {}", dateStr, number);
            }
        }

        if (hasNew) {
            casePlan.setNotifiedRedActions(alreadyNotified);
            casePlanRepository.save(casePlan);
        }
    }
}