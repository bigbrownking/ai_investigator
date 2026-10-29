package org.di.digital.security.zonal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.exception.message.AccessDeniedMessage;
import org.di.digital.model.enums.plan.ApprovalLevel;
import org.di.digital.model.user.Region;
import org.di.digital.model.user.User;
import org.di.digital.repository.user.RegionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;
import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@Slf4j
@Component("reportingAccess")
@RequiredArgsConstructor
public class ReportingAccess {

    public static final String ADMIN_OR_AFM_REG_ADMIN =
            "hasAuthority('ADMIN') or (hasAuthority('REG_ADMIN') and @reportingAccess.isRegAfm())";

    public static final String VIEW_ZONAL_TABLES =
            "hasAuthority('ADMIN') or (hasAuthority('REG_ADMIN') and (@reportingAccess.isRegAfm() or @reportingAccess.isDeputyDepartmentHead()))";

    public static final String ZONAL_TABLE_ACCESS =
            "hasAuthority('ZONAL') or (hasAuthority('ADVANCED_USER') and @reportingAccess.isDirectorateHead())";

    public static final long AFM_REGION_ID = 1L;

    public static final String ADMIN_OR_REG_ADMIN =
            "hasAuthority('ADMIN') or hasAuthority('REG_ADMIN')";

    public boolean isRegAfm() {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }
        Region region = user.getRegion();
        return region != null && region.getId() != null && region.getId() == AFM_REGION_ID;
    }

    /** Профессии «Руководитель управления» и «Зам. руководителя управления» (id из ApprovalLevel). */
    public boolean isDirectorateHead() {
        User user = getCurrentUser();
        if (user == null || user.getProfession() == null) {
            return false;
        }
        Long professionId = user.getProfession().getId();
        return ApprovalLevel.LEVEL_1_RUK.getProfessionId().equals(professionId)
                || ApprovalLevel.LEVEL_1_ZAM.getProfessionId().equals(professionId);
    }

    /** Профессия «Зам. руководителя департамента» (id берётся из ApprovalLevel.LEVEL_FINAL). */
    public boolean isDeputyDepartmentHead() {
        User user = getCurrentUser();
        return user != null
                && user.getProfession() != null
                && ApprovalLevel.LEVEL_FINAL.getProfessionId().equals(user.getProfession().getId());
    }

    /**
     * Регионы, таблицы которых видит текущий пользователь.
     * null: без ограничений (ADMIN или рег-админ АФМ); иначе только регионы этого рег-админа.
     */
    public Set<Long> visibleRegionIds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()));
        boolean regAfm = hasAuthority(auth, "REG_ADMIN") && isRegAfm();
        if (admin || regAfm) {
            return null;
        }
        User user = getCurrentUser();
        if (user == null || user.getRegion() == null) {
            throw new AccessDeniedException(AccessDeniedMessage.ADMIN_NO_REGION.localized(getCurrentLang()));
        }
        return Set.of(user.getRegion().getId());
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    public void assertCanEditRegion(Long targetRegionId) {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        // ADMIN может редактировать любой регион.
        if (hasAuthority(auth, "ADMIN")) {
            return;
        }

        // REG_ADMIN из АФМ также может редактировать любой регион.
        if (hasAuthority(auth, "REG_ADMIN") && isRegAfm()) {
            return;
        }

        // Другим ролям этот метод недоступен.
        if (!hasAuthority(auth, "REG_ADMIN")) {
            throw new AccessDeniedException(
                    AccessDeniedMessage.REGION_OUT_OF_REGION
                            .localized(getCurrentLang())
            );
        }

        User user = getCurrentUser();

        if (user == null) {
            throw new AccessDeniedException(
                    AccessDeniedMessage.REGION_OUT_OF_REGION
                            .localized(getCurrentLang())
            );
        }

        // Регион, указанный у пользователя.
        boolean isOwnRegion =
                user.getRegion() != null
                        && targetRegionId.equals(user.getRegion().getId());


        if (!isOwnRegion) {
            throw new AccessDeniedException(
                    AccessDeniedMessage.REGION_OUT_OF_REGION
                            .localized(getCurrentLang())
            );
        }
    }
}