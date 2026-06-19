package org.di.digital.repository.user;

import org.di.digital.model.user.Region;
import org.di.digital.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {
    List<Region> findAllByOrderByRuNameAsc();
    Optional<Region> findByRuName(String ruName);
    long countByAdminsContaining(User user);
    List<Region> findByAdminsContaining(User user);

}
