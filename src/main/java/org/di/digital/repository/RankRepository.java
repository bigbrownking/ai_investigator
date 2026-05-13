package org.di.digital.repository;

import org.di.digital.model.Rank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RankRepository extends JpaRepository<Rank, Long> {
    List<Rank> findAllByOrderByNameAsc();
    Optional<Rank> findByName(String name);

}
