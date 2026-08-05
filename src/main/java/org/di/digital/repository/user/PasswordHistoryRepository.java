package org.di.digital.repository.user;

import java.util.List;

import org.di.digital.model.user.User;
import org.di.digital.model.user.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findTop5ByUserOrderByChangedAtDesc(User user);

    List<PasswordHistory> findByUserOrderByChangedAtDesc(User user);


    
}
