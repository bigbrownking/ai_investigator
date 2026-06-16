package org.di.digital.util.requests;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.user.User;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.di.digital.security.UserDetailsImpl;
import org.di.digital.service.LogService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    public static void validateRegionAccess(User admin,
                                      Case caseEntity) {

        if (admin.getRegion() == null) {
            throw new AccessDeniedException("У администратора нет региона");
        }

        User owner = caseEntity.getOwner();

        if (owner == null || owner.getRegion() == null
                || !owner.getRegion().getId().equals(admin.getRegion().getId())) {
            throw new AccessDeniedException("Дело не принадлежит вашему региону");
        }
    }
    public static boolean isRegAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().equals(ROLE_REG_ADMIN));
    }
}
