package org.di.digital.reporting.integration;

import org.di.digital.model.user.User;
import org.di.digital.security.UserDetailsImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the current user from Spring Security. This adapter is the only reporting class that
 * touches User/Region; it hands the rest of the module plain ids.
 */
@Component
public class SecurityReportingUserContext implements ReportingUserContext {

    @Override
    public Long currentOperatorId() {
        return currentPrincipal().getId();
    }

    /**
     * Region from users.region_id. User.region is fetched EAGER when UserDetailsImpl is built,
     * so this works outside of a Hibernate session.
     * Note: REG_ADMIN regions are also assigned via region_admins (possibly several); this uses users.region_id only.
     */
    @Override
    public Long currentRegionId() {
        User user = currentPrincipal().getUser();
        if (user == null || user.getRegion() == null || user.getRegion().getId() == null) {
            throw new AccessDeniedException("Current user has no region assigned; reporting requires one");
        }
        return user.getRegion().getId();
    }

    private UserDetailsImpl currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails;
        }
        throw new AccessDeniedException("Reporting requires an authenticated user");
    }
}
