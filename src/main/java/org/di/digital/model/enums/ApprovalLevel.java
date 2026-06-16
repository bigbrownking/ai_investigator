package org.di.digital.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ApprovalLevel {

    // profession_id=3  Зам. руководителя управления
    LEVEL_1_ZAM(3L, PlanStatus.PENDING, PlanStatus.APPROVED_L1, 1),

    // profession_id=4  Руководитель управления
    LEVEL_1_RUK(4L, PlanStatus.PENDING, PlanStatus.APPROVED_L2, 1),

    // profession_id=7  Зам. департамента (финальное утверждение)
    LEVEL_FINAL(7L, PlanStatus.APPROVED_L1, PlanStatus.APPROVED_L3, 2);

    private final Long professionId;
    private final PlanStatus requiredStatus;
    private final PlanStatus approvedStatus;
    private final int level;


    public static ApprovalLevel fromProfession(Long professionId) {
        return Arrays.stream(values())
                .filter(l -> l.professionId.equals(professionId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException(
                        "У вас нет прав для согласования плана"
                ));
    }

    public boolean isFirstLevel() {
        return this == LEVEL_1_ZAM || this == LEVEL_1_RUK;
    }

    public boolean isFinalLevel() {
        return this == LEVEL_FINAL;
    }
}