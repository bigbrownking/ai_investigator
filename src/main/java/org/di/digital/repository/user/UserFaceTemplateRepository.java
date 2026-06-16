package org.di.digital.repository.user;

import org.di.digital.model.user.User;
import org.di.digital.model.user.UserFaceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface UserFaceTemplateRepository extends JpaRepository<UserFaceTemplate, Long> {
    List<UserFaceTemplate> findByUserAndRevokedAtIsNull(User user);
    void deleteByUser(User user);
}
