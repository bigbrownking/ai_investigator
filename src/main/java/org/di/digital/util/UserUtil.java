package org.di.digital.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.Case;
import org.di.digital.model.User;
import org.di.digital.security.UserDetailsImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.StringTokenizer;

@Slf4j
public class UserUtil {
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
            log.warn("Access denied: User {} tried to access case {} chat", user.getEmail(), caseEntity.getNumber());
            throw new AccessDeniedException("You don't have permission to access this case's chat");
        }
    }
}
