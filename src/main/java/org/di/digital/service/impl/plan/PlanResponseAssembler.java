package org.di.digital.service.impl.plan;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.plan.CasePlanResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.user.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PlanResponseAssembler {

    public CasePlanResponse build(Case caseEntity) {
        return CasePlanResponse.builder()
                .planStatus(caseEntity.getPlanStatus())
                .canWithdraw(canWithdraw(caseEntity.getPlanStatus()))
                .approvedBy(getApproverName(caseEntity))
                .reviewedBy(getReviewerName(caseEntity))
                .plan(enrichPlanWithStatus(caseEntity.getPlan()))
                .build();
    }

    public boolean canWithdraw(PlanStatus status) {
        return status == PlanStatus.PENDING
                || status == PlanStatus.APPROVED_L1
                || status == PlanStatus.APPROVED_L2;
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> enrichPlanWithStatus(Map<String, Object> plan) {
        if (plan == null) return null;

        Map<String, Object> result = new LinkedHashMap<>(plan);
        List<Map<String, Object>> actions = (List<Map<String, Object>>) result.get("actions");
        if (actions == null) {
            log.info("No actions in plan to enrich");
            return result;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> enriched = actions.stream().map(action -> {
            Map<String, Object> a = new LinkedHashMap<>(action);
            String срокStr = (String) action.get("срок");
            if (срокStr != null) {
                try {
                    LocalDate deadline = LocalDate.parse(срокStr, formatter);
                    long daysUntil = ChronoUnit.DAYS.between(today, deadline);
                    String цвет = daysUntil <= 1 ? "красный" : daysUntil <= 3 ? "желтый" : "серый";
                    a.put("цвет", цвет);
                } catch (Exception e) {
                    log.warn("Не удалось разобрать срок '{}' для действия {}", срокStr, action.get("номер"));
                }
            }
            return a;
        }).toList();

        result.put("actions", enriched);
        return result;
    }
}