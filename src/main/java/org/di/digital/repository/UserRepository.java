package org.di.digital.repository;

import org.di.digital.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByIin(String iin);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.settings WHERE u.email = :email")
    Optional<User> findByEmailWithSettings(@Param("email") String email);
    boolean existsByEmail(String email);
    Page<User> findByRegionId(Long regionId, Pageable pageable);
    long countByActiveTrue();
    long countByActiveFalse();
    long countByRegionId(Long regionId);
    long countByRegionIdAndActiveTrue(Long regionId);
}
