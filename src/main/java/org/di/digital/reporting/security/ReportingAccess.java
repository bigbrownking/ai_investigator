package org.di.digital.reporting.security;

import org.di.digital.model.user.Region;
import org.di.digital.model.user.User;
import org.springframework.stereotype.Component;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@Component("reportingAccess")
public class ReportingAccess {

    public static final String ADMIN_OR_AFM_REG_ADMIN =
            "hasAuthority('ADMIN') or (hasAuthority('REG_ADMIN') and @reportingAccess.isRegAfm())";

    public static final long AFM_REGION_ID = 1L;

    public boolean isRegAfm() {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }
        Region region = user.getRegion();
        return region != null && region.getId() != null && region.getId() == AFM_REGION_ID;
    }
}