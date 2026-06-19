package org.di.digital.model.enums;

import lombok.Getter;

@Getter
public enum PlanNotificationType {
    PLAN_STATUS_CHANGED("PLAN_STATUS_CHANGED"),
    PLAN_ACTION_OVERDUE("PLAN_ACTION_OVERDUE");
    private final String description;

    PlanNotificationType(String description) {
        this.description = description;
    }
}
