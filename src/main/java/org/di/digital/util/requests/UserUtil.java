package org.di.digital.util.requests;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.user.Region;
import org.di.digital.model.user.User;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.repository.user.RegionRepository;
import org.di.digital.security.UserDetailsImpl;
import org.di.digital.service.LogService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.StringTokenizer;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserUtil {
    private static final String ROLE_REG_ADMIN = "REG_ADMIN";
    private static LogService logService;
    public static String getClientIpAddress(HttpServletRequest request) {
        String forwardHeader= request.getHeader("X-Forwarded-For");
        if (forwardHeader == null) {
            return request.getRemoteAddr();
        } else {
            return new StringTokenizer(forwardHeader, ",").nextToken().trim();
        }
    }
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getUser();
        }

        return null;
    }
    public static HttpServletRequest getCurrentHttpRequest(){
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
    public static void validateUserAccess(Case caseEntity, User user) {
        if (!caseEntity.isOwner(user) && !caseEntity.hasUser(user)) {
            logService.log(
                    String.format("No access for case %s", caseEntity.getNumber()),
                    LogLevel.ERROR,
                    LogAction.NO_ACCESS,
                    caseEntity.getNumber(),
                    user.getEmail()
            );
            throw new AccessDeniedException("У вас нет доступа к этому делу");
        }
    }
    public static void validateOwnerAccess(Case caseEntity, User user) {
        if (!caseEntity.isOwner(user)) {
            logService.log(
                    String.format("Non-owner user %s attempted to manage members of case %s",
                            user.getEmail(), caseEntity.getNumber()),
                    LogLevel.ERROR,
                    LogAction.NO_ACCESS,
                    caseEntity.getNumber(),
                    user.getEmail()
            );
            throw new AccessDeniedException("Только владелец дела может управлять участниками");
        }
    }
    public static void validateRegionAccess(User admin, Case caseEntity, RegionRepository regionRepository) {
        if (admin.getRegion() == null) {
            throw new AccessDeniedException("У администратора нет региона");
        }

        User owner = caseEntity.getOwner();
        if (owner == null || owner.getRegion() == null) {
            throw new AccessDeniedException("Дело не принадлежит вашему региону");
        }

        List<Region> adminRegions = regionRepository.findByAdminsContaining(admin);
        boolean hasAccess = adminRegions.stream()
                .anyMatch(r -> r.getId().equals(owner.getRegion().getId()));

        if (!hasAccess) {
            throw new AccessDeniedException("Дело не принадлежит вашему региону");
        }
    }
    public static void validateUserRegionAccess(User admin, User user, RegionRepository regionRepository) {
        List<Region> adminRegions = regionRepository.findByAdminsContaining(admin);

        if (adminRegions.isEmpty()) {
            throw new AccessDeniedException("У администратора нет регионов");
        }

        List<Long> regionIds = adminRegions.stream().map(Region::getId).toList();

        if (user.getRegion() == null || !regionIds.contains(user.getRegion().getId())) {
            throw new AccessDeniedException("Этот пользователь не принадлежит вашему региону");
        }
    }
    public static List<Long> getAdminRegionIds(User admin, RegionRepository regionRepository) {
        return regionRepository.findByAdminsContaining(admin)
                .stream().map(Region::getId).toList();
    }

    public static void validateAppealRegionAccess(User admin, Appeal appeal, RegionRepository regionRepository) {
        List<Long> regionIds = getAdminRegionIds(admin, regionRepository);

        if (appeal.getRegion() == null || !regionIds.contains(appeal.getRegion().getId())) {
            throw new AccessDeniedException("Это обращение не принадлежит вашему региону");
        }
    }
    public static boolean isRegAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().equals(ROLE_REG_ADMIN));
    }
    public static List<Region> getAdminRegions(User admin, RegionRepository regionRepository) {
        List<Region> regions = regionRepository.findByAdminsContaining(admin);
        if (regions.isEmpty()) {
            throw new IllegalStateException("У админа нет ответственных регионов");
        }
        return regions;
    }
}
