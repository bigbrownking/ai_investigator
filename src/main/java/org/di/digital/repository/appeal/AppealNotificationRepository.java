package org.di.digital.repository.appeal;

import org.di.digital.model.appeal.AppealNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppealNotificationRepository extends JpaRepository<AppealNotification, Long> {
    List<AppealNotification> findTop4ByUserEmailOrderByCreatedAtDesc(String userEmail);
    long countByUserEmailAndIsReadFalse(String userEmail);
    List<AppealNotification> findAllByUserEmailAndIsReadFalse(String userEmail);
}
