package org.di.digital.service.appeal;

import org.di.digital.model.appeal.AppealNotification;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.user.User;

import java.util.List;

public interface AppealService {
    List<AppealNotification> getMyNotifications(String email);
    void markAllRead(String email);
    void markOneRead(Long id, String email);
    long getUnreadCount(String email);

    void createNewAppealNotification(Appeal appeal, User applicant);
    void createAppealReviewedNotification(Appeal appeal, User reviewer);
}
